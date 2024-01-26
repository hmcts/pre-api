from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports, get_user_id
import uuid


class BookingManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("""  SELECT *
                                        FROM public.recordings
                                        WHERE parentrecuid = recordinguid and recordingversion = '1'""")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        destination_cursor.execute("SELECT id FROM public.cases")
        cases_data = destination_cursor.fetchall()
        existing_case_ids = {case[0] for case in cases_data} 

        destination_cursor.execute(
            """CREATE TABLE IF NOT EXISTS public.temp_recordings (
                capture_session_id UUID,
                recording_id UUID,
                booking_id UUID,
                parent_recording_id UUID,
                case_id UUID,
                scheduled_for TIMESTAMPTZ,
                deleted_at TIMESTAMPTZ,
                created_at TIMESTAMPTZ,
                modified_at TIMESTAMPTZ,
                created_by UUID,
                started_by_user_id UUID,
                ingest_address VARCHAR(255),
                live_output_url VARCHAR(255),
                status TEXT
            )
            """
        )

        for recording in source_data:
            case_id = recording[1]
            recording_id = recording[0]
            booking_id = str(uuid.uuid4())
            scheduled_for = parse_to_timestamp(recording[10])

            if scheduled_for is None:
                self.failed_imports.add(('bookings', booking_id, f'Scheduled for date is NULL for case id: {case_id}'))
                continue

            recording_status = recording[11]
            created_at = parse_to_timestamp(recording[22])
            deleted_at = None
            if recording_status == 'Deleted':
                if recording[24]:
                    deleted_at = parse_to_timestamp(recording[24])
                else:
                    deleted_at = created_at

            modified_at = parse_to_timestamp(recording[24]) if recording[24] is not None else created_at
            created_by =  get_user_id(destination_cursor,recording[21])

            # Check if the case has been migrated into the cases table 
            if case_id not in existing_case_ids:
                self.failed_imports.add(('bookings', booking_id, f'Case ID {case_id} not found in cases data'))
                continue
            
            # Insert into temp table 
            if not check_existing_record(destination_cursor,'temp_recordings', 'recording_id', recording_id):
                try:
                    destination_cursor.execute(
                        """
                        INSERT INTO public.temp_recordings 
                            (case_id, recording_id, booking_id,scheduled_for, deleted_at, created_at, created_by,modified_at )
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                        """,
                        (   case_id, recording_id, booking_id,scheduled_for,deleted_at, created_at, 
                            created_by if created_by is not None else None,
                            modified_at if modified_at is not None else None
                        ),
                    )
                except Exception as e:
                    self.failed_imports.add(('temp_recordings', recording_id, f'Failed to insert into temp_recordings: {e}'))
                    continue

        # Fetch temp data
        destination_cursor.execute("SELECT * FROM public.temp_recordings")
        temp_recordings_data = destination_cursor.fetchall()

        for booking in temp_recordings_data:
            id = booking[2]
            case_id = booking[4]

            if not check_existing_record(destination_cursor,'bookings', 'id', id):   
                try:
                    scheduled_for = booking[5]
                    created_at = booking[7]
                    modified_at = booking[8]
                    created_by = booking[9]
                    deleted_at = booking[6]

                    destination_cursor.execute(
                        """
                        INSERT INTO public.bookings 
                            (id, case_id, scheduled_for, created_at, modified_at, deleted_at)
                        VALUES (%s, %s, %s, %s, %s, %s )
                        """,
                        (id, case_id, scheduled_for, created_at, modified_at, deleted_at),
                    )

                    audit_entry_creation(
                        destination_cursor,
                        table_name="bookings",
                        record_id=id,
                        record=case_id,
                        created_at=created_at,
                        created_by=created_by if created_by is not None else None,
                    )
                except Exception as e:  
                    self.failed_imports.add(('bookings', id,e))
                    
        log_failed_imports(self.failed_imports)
    
   
