from .helpers import check_existing_record, audit_entry_creation, log_failed_imports, parse_to_timestamp
import uuid

class CaptureSessionManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT DISTINCT ON (parentrecuid) * FROM public.recordings WHERE recordingversion = '1' and recordingstatus != 'No Recording'")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        destination_cursor.execute("SELECT * FROM public.temp_recordings")
        temp_recording_data = destination_cursor.fetchall()

        destination_cursor.execute("SELECT * FROM public.users")
        user_data = destination_cursor.fetchall()

        for recording in source_data:
            recording_id = recording[0]
            capture_session_id = str(uuid.uuid4())
            booking_id = next((temp_rec[2] for temp_rec in temp_recording_data if temp_rec[1] == recording_id), None)
            parent_recording_id = recording[9]
            ingest_address = recording[8] 
            live_output_url = recording[20]
            started_by = recording[21]
            started_by_user_id = next((user[0] for user in user_data if user[3] == started_by), None)
            deleted_at = parse_to_timestamp(recording[24]) if str(recording[11]).lower() == 'deleted' else None
            # started_at =  ?
            # finished_at = ?
            # status = ?
    
            try: 
                destination_cursor.execute(
                    """
                    UPDATE public.temp_recordings
                    SET capture_session_id = %s, parent_recording_id = %s, deleted_at=%s, started_by_user_id=%s
                    WHERE recording_id = %s 
                    """,
                    (capture_session_id, parent_recording_id, deleted_at, started_by_user_id, recording_id),
                )
                destination_cursor.connection.commit()
                   
            except Exception as e:
                self.failed_imports.add(('temp_recordings', recording_id, f'Failed to insert into temp_recordings: {e}'))
                continue

        # inserting only version 1 into capture sessions as this would be the parent recording
        destination_cursor.execute("SELECT * FROM public.temp_recordings WHERE recording_id = parent_recording_id")
        temp_recording_data = destination_cursor.fetchall()

        destination_cursor.execute("SELECT * FROM public.users")
        user_data = destination_cursor.fetchall()


        for temp_recording in temp_recording_data:
            id = temp_recording[0]
            booking_id = temp_recording[2]
            deleted_at = temp_recording[6]
            started_by_user_id = temp_recording[10] 
            finished_by_user_id = temp_recording[10] 
            created_at = temp_recording[7]
            modified_at = temp_recording[8]
      
                
            if not check_existing_record(destination_cursor,'bookings', 'id', booking_id):
                self.failed_imports.add(('capture_sessions', id, f"Booking id: {booking_id} not recorded in bookings table"))
                continue

            if not check_existing_record(destination_cursor,'capture_sessions','id', id):
                origin = 'PRE'
                
                try:
                    destination_cursor.execute(
                        """
                        INSERT INTO public.capture_sessions (id, booking_id, origin, ingest_address, live_output_url, deleted_at, started_by_user_id, finished_by_user_id)
                        VALUES (%s, %s, %s,%s,%s, %s,%s,%s)
                        """,
                        ( id, booking_id, origin, ingest_address, live_output_url, deleted_at, started_by_user_id, finished_by_user_id),  
                    )
                    destination_cursor.connection.commit()
      
                    audit_entry_creation(
                        destination_cursor,
                        table_name="capture_sessions",
                        record_id=id,
                        record=booking_id,
                        created_at=created_at,
                        modified_at=modified_at
                    )
                except Exception as e:  
                    self.failed_imports.add(('capture_sessions', id,e))
                
        log_failed_imports(self.failed_imports)
