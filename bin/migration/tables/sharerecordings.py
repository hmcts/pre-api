from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports


class ShareRecordingsManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.videopermissions")
        return self.source_cursor.fetchall()
    
    def get_capture_session(self, recordings_data, recording_id):
        capture_session_id = next((recording[1] for recording in recordings_data if recording[0] == recording_id), None)
        return capture_session_id
    
    def get_user(self, users_data,  user_email):
        user_id = next((user[0] for user in users_data if user[3] == user_email), None)
        return user_id

    def migrate_data(self, destination_cursor, source_data):
        batch_share_recordings_data = []

        destination_cursor.execute("SELECT * FROM public.recordings")
        recordings_data = destination_cursor.fetchall()

        destination_cursor.execute("SELECT * FROM public.users")
        users_data = destination_cursor.fetchall()

        for video_permission in source_data:
            id = video_permission[0]

            recording_id = video_permission[1]
            capture_session_id = self.get_capture_session( recordings_data, recording_id)

            shared_with_user_id = video_permission[4]

            created_by = video_permission[18]
            shared_by_user_id = self.get_user(users_data, created_by)

            created_at = parse_to_timestamp(video_permission[19])
            deleted_at = parse_to_timestamp(video_permission[21]) if video_permission[15] != "True" else None

            if not capture_session_id:
                self.failed_imports.add(('share_recordings', id, f"No capture session id found for recordinguid: {recording_id}"))
                continue

            if not check_existing_record(destination_cursor,'users','id',shared_with_user_id):
                self.failed_imports.add(('share_recordings', id, f"Invalid shared_with_user_id value: {shared_with_user_id}"))
                continue

            if not shared_by_user_id:
                self.failed_imports.add(('share_recordings', id, f"No user found for shared_with_user email : {created_by}"))
                continue

            batch_share_recordings_data.append((id, capture_session_id, shared_with_user_id, shared_by_user_id,created_at, deleted_at))
            
        try:
            if batch_share_recordings_data:
                 destination_cursor.executemany(
                    """
                    INSERT INTO public.share_recordings 
                        (id, capture_session_id, shared_with_user_id, shared_by_user_id,created_at, deleted_at)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    batch_share_recordings_data,
                )
            destination_cursor.connection.commit()

            for entry in batch_share_recordings_data:
                audit_entry_creation(
                    destination_cursor,
                    table_name="share_recordings",
                    record_id=entry[0],
                    record=entry[1],
                    created_at=entry[4],
                    created_by=entry[3],
                )

        except Exception as e:
            self.failed_imports.add(('share_recordings', id, e))

        log_failed_imports(self.failed_imports)
        

