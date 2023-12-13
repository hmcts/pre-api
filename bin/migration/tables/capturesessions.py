from .helpers import check_existing_record, audit_entry_creation, log_failed_imports
import uuid

class CaptureSessionManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT DISTINCT ON (parentrecuid) * FROM public.recordings")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        # creating a temporary table for the unique recordings and capture session values
        destination_cursor.execute(
            """CREATE TABLE IF NOT EXISTS public.temp_recordings (
                capture_session_id UUID,
                recording_id UUID,
                booking_id UUID,
                parent_recording_id UUID
            )
            """
        )

        for recording in source_data:
            recording_id = recording[0]

            destination_cursor.execute(
                """SELECT * FROM public.temp_recordings WHERE recording_id = %s""",
                (recording_id,)
            )
            existing_record_in_temp_table = destination_cursor.fetchone()

            if not existing_record_in_temp_table:
                capture_session_id = str(uuid.uuid4())
                booking_id = recording[1]
                parent_recording_id = recording[9]

                destination_cursor.execute(
                    """ INSERT INTO public.temp_recordings (capture_session_id, recording_id, booking_id, parent_recording_id) 
                        VALUES (%s, %s, %s,%s)""",
                    (capture_session_id, recording_id, booking_id, parent_recording_id),
                )

        destination_cursor.execute("SELECT * FROM public.temp_recordings WHERE recording_id = parent_recording_id")
        temp_recording_data = destination_cursor.fetchall()

        for temp_recording in temp_recording_data:
            id = temp_recording[0]
            booking_id = temp_recording[2]

            if check_existing_record(destination_cursor,'bookings', 'id', booking_id) and not check_existing_record(destination_cursor,'capture_sessions','id', id):
                origin = 'PRE'
                ingest_address = recording[8] 
                live_output_url = recording[20]
                # started_at =  ?
                # started_by_user_id = ?
                # finished_at = ?
                # finished_by_user_id = ?
                # status = ?

                try:
                    destination_cursor.execute(
                        """
                        INSERT INTO public.capture_sessions ( id, booking_id, origin, ingest_address, live_output_url)
                        VALUES (%s, %s, %s,%s,%s)
                        """,
                        ( id, booking_id, origin, ingest_address, live_output_url),  
                    )

                    audit_entry_creation(
                        destination_cursor,
                        table_name="capture_sessions",
                        record_id=id,
                        record=booking_id,
                    )
                except Exception as e:  
                    self.failed_imports.add(('capture_sessions', id,e))
            else:
                self.failed_imports.add(('capture_sessions', id))
                
        log_failed_imports(self.failed_imports)
