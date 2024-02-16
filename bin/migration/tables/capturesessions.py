from .helpers import check_existing_record, audit_entry_creation, parse_to_timestamp
import uuid


class CaptureSessionManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute(
            "SELECT * FROM public.recordings WHERE parentrecuid = recordinguid AND recordingstatus != 'No Recording' AND NOT (recordingstatus = 'Deleted' AND ingestaddress IS NULL)")
        return self.source_cursor.fetchall()

    def check_record_in_temp_table(self, destination_cursor, recording_id):
        destination_cursor.execute(
            "SELECT EXISTS (SELECT 1 FROM public.temp_recordings WHERE recording_id=%s)", (recording_id,))
        result = destination_cursor.fetchone()

        if result[0]:
            destination_cursor.execute(
                "SELECT EXISTS (SELECT 1 FROM public.temp_recordings WHERE capture_session_id IS NULL AND recording_id=%s)", (recording_id,))
            capture_session_id_null = destination_cursor.fetchone()[0]

            if capture_session_id_null:
                return True, None
            else:
                return False, None
        else:
            return False, True

    def map_recording_status(self, status):
        status_lower = status.lower()
        result = None

        # https://tools.hmcts.net/confluence/pages/viewpage.action?spaceKey=S28&title=NRO+-+Application+Statuses
        if status_lower in ["no stream detected", "ready to record", "ready to stream", "checking stream..."]:
            result = "STANDBY"
        elif status_lower in ["initiating request...", "ready to record"]:
            result = "INITIALISING"
        elif status_lower == "recording":
            result = "RECORDING"
        elif status_lower == "mp4 ready for viewing":
            result = "RECORDING_AVAILABLE"
        elif status_lower == "Finished Recording - Processing...":
            result = "PROCESSING"
        elif status_lower == "error - failed to start":
            result = "FAILURE"
        elif status_lower == "no recording available":
            result = "NO_RECORDING"

        return result

    def get_recording_date(self, recording_id, activity):
        query = """
            SELECT createdon
            FROM public.audits
            WHERE activity = %s
            AND recordinguid = %s
        """
        self.source_cursor.execute(query, (activity, recording_id))
        result = self.source_cursor.fetchone()
        return parse_to_timestamp(result[0]) if result else None

    def migrate_data(self, destination_cursor, source_data):
        temp_recording_data = []
        user_data = []
        try: 
            destination_cursor.execute("SELECT * FROM public.temp_recordings")
            temp_recording_data = destination_cursor.fetchall()

            destination_cursor.execute("SELECT * FROM public.users")
            user_data = destination_cursor.fetchall()
        except Exception as e:
            print(e)

        temp_recording_batch = []
        for recording in source_data:
            recording_id = recording[0]
            parent_recording_id = recording[9]

            if parent_recording_id is None:
                self.failed_imports.append({
                    'table_name': 'capture_sessions',
                    'table_id': recording_id,
                    'recording_id': recording_id,
                    'details': f"Parent recording ID is blank for recording ID: {recording_id}"
                })
                continue

            ingest_address = recording[8]
            live_output_url = recording[20]
            started_by = recording[21]
            started_by_user_id = next(
                (user[0] for user in user_data if user[3] == started_by), None)
            deleted_at = parse_to_timestamp(recording[24]) if str(
                recording[11]).lower() == 'deleted' else None
            status = self.map_recording_status(recording[11])

            result, booking_not_in_temp_table = self.check_record_in_temp_table(
                destination_cursor, recording_id)
            if result:
                capture_session_id = str(uuid.uuid4())
                temp_recording_batch.append((capture_session_id, parent_recording_id, deleted_at,
                                            started_by_user_id,  ingest_address, live_output_url, status, recording_id))
            elif booking_not_in_temp_table:
                self.failed_imports.append({
                    'table_name': 'capture_sessions',
                    'table_id': recording_id,
                    'recording_id': recording_id,
                    'details': f"Booking not in temporary recordings table for recording ID: {recording_id}"
                })
                continue

        if temp_recording_batch:
            try:
                destination_cursor.executemany(
                    """
                    UPDATE public.temp_recordings
                    SET capture_session_id = %s, parent_recording_id = %s, deleted_at=%s,
                        started_by_user_id=%s, ingest_address=%s, live_output_url=%s, status=%s
                    WHERE recording_id = %s
                    """,
                    temp_recording_batch
                )
                destination_cursor.connection.commit()

            except Exception as e:
                self.failed_imports.append(
                    {'table_name': 'capture_sessions', 'table_id': recording_id, 'recording_id': recording_id, 'details': str(e)})

        # inserting only version 1 into capture sessions as this would be the parent recording
        destination_cursor.execute(
            "SELECT * FROM public.temp_recordings WHERE recording_id = parent_recording_id")
        temp_recording_data = destination_cursor.fetchall()

        destination_cursor.execute("SELECT * FROM public.users")
        user_data = destination_cursor.fetchall()

        capture_session_batch = []
        for temp_recording in temp_recording_data:
            id = temp_recording[0]
            recording_id = temp_recording[1]
            booking_id = temp_recording[2]
            deleted_at = temp_recording[6]
            started_by_user_id = temp_recording[10]
            finished_by_user_id = temp_recording[10]
            created_at = temp_recording[7]
            ingest_address = temp_recording[11]
            live_output_url = temp_recording[12]
            status = temp_recording[13]
            started_at = self.get_recording_date(
                recording_id, 'Start Recording Clicked') or created_at
            finished_at = self.get_recording_date(
                recording_id, 'Finish Recording') or created_at

            if not check_existing_record(destination_cursor, 'bookings', 'id', booking_id) or booking_id is None:
                self.failed_imports.append({
                    'table_name': 'capture_sessions',
                    'table_id': recording_id,
                    'recording_id': recording_id,
                    'details': f"Booking ID: {booking_id} not recorded in bookings table"
                })
                continue

            if not check_existing_record(destination_cursor, 'capture_sessions', 'id', id):
                origin = 'PRE'

                audit_entry_creation(
                    destination_cursor,
                    table_name="capture_sessions",
                    record_id=id,
                    record=booking_id,
                    created_at=created_at,
                )
                capture_session_batch.append((id, booking_id, origin, ingest_address, live_output_url,
                                             deleted_at, started_by_user_id, finished_by_user_id, status, started_at, finished_at))

        try:
            destination_cursor.executemany(
                """
                INSERT INTO public.capture_sessions (id, booking_id, origin, ingest_address, live_output_url, deleted_at, started_by_user_id, finished_by_user_id, status, started_at, finished_at)
                VALUES (%s, %s, %s,%s,%s, %s,%s,%s, %s,%s, %s)
                """,
                capture_session_batch
            )
            destination_cursor.connection.commit()

        except Exception as e:
            destination_cursor.connection.rollback()
            self.failed_imports.append(
                {'table_name': 'capture_sessions', 'table_id': recording_id, 'recording_id': recording_id, 'details': str(e)})

        self.logger.log_failed_imports(self.failed_imports)
