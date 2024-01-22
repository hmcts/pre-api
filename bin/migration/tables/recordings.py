from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports


class RecordingManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("""  SELECT *
                                        FROM public.recordings
                                        WHERE (recordingavailable IS NULL OR 
                                            recordingavailable NOT ILIKE 'false' AND 
                                            recordingavailable NOT ILIKE 'no')""")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        #  first inserting the recordings with multiple recordings versions - this is to satisfy the parent_recording_id FK constraint
        parent_recording_ids = [recording[9] for recording in source_data]
        seen = set()
        duplicate_parent_ids = set()
        
        for recording_id in parent_recording_ids:
            if recording_id in seen:
                duplicate_parent_ids.add(recording_id)
            else:
                seen.add(recording_id)

        duplicate_parent_id_records = [recording for recording in source_data if recording[0] in duplicate_parent_ids]
        non_duplicate_parent_id_records = [recording for recording in source_data if recording[0] not in duplicate_parent_ids]

        for recording in duplicate_parent_id_records:
            id = recording[0]
            parent_recording_id = recording[9]

            if parent_recording_id not in (rec[0] for rec in source_data):
                self.failed_imports.add(('recordings', id, f'Parent recording id: {parent_recording_id} does not match a recording id'))
                continue
            
            destination_cursor.execute("SELECT capture_session_id FROM public.temp_recordings WHERE parent_recording_id = %s", (parent_recording_id,)) 
            result = destination_cursor.fetchone()

            if result is None: 
                self.failed_imports.add(('recordings', id, f'No capture_session id found for parent recording id {parent_recording_id}'))
                continue

            capture_session_id = result[0]

            if not check_existing_record(destination_cursor,'capture_sessions', 'id', capture_session_id):
                self.failed_imports.add(('recordings', id, f'Recording not captured in capture sessions with capture_session_id {capture_session_id}'))
                continue

            if not check_existing_record(destination_cursor,'recordings', 'id', id):
                version = recording[12] 
                url = recording[20] if recording[20] is not None else 'Unknown URL'
                filename = recording[14]
                created_at = parse_to_timestamp(recording[22])
                modified_at = parse_to_timestamp(recording[24])
                created_by = recording[21]
                recording_status = recording[11]
                deleted_at = parse_to_timestamp(recording[24]) if recording_status == 'Deleted' else None

                try:
                    destination_cursor.execute(
                        """
                        INSERT INTO public.recordings (id, capture_session_id, parent_recording_id, version, url, filename, created_at, deleted_at)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                        """,
                        (id, capture_session_id, parent_recording_id, version, url, filename, created_at, deleted_at),  
                    )

                    audit_entry_creation(
                        destination_cursor,
                        table_name="recordings",
                        record_id=id,
                        record=capture_session_id,
                        created_at=created_at,
                        created_by=created_by,
                    )

                except Exception as e:  
                    self.failed_imports.add(('recordings', id, e))

        # inserting remaining records
        for recording in non_duplicate_parent_id_records:
            id = recording[0]
            parent_recording_id = recording[9]
        
            destination_cursor.execute("SELECT capture_session_id from public.temp_recordings where parent_recording_id = %s",(parent_recording_id,)) 
            result = destination_cursor.fetchone()

            if result is None:
                self.failed_imports.add(('recordings', id, f'No capture_session id found for parent recording id {parent_recording_id}'))
                continue

            capture_session_id = result[0]

            if not check_existing_record(destination_cursor,'capture_sessions', 'id', capture_session_id):
                self.failed_imports.add(('recordings', id, f'Recording not captured in capture sessions with capture_session_id {capture_session_id}'))
                continue

            if not check_existing_record(destination_cursor,'recordings', 'id', id,):
                version = recording[12] 
                url = recording[20] if recording[20] is not None else 'Unknown URL'
                filename = recording[14]
                created_at = parse_to_timestamp(recording[22])
                recording_status = recording[11]
                deleted_at = parse_to_timestamp(recording[24]) if recording_status == 'Deleted' else None
        #         duration =  ? - this info is in the asset files on AMS 
        #         edit_instruction = ?
            
                try:
                    destination_cursor.execute(
                        """
                        INSERT INTO public.recordings (id, capture_session_id, parent_recording_id, version, url, filename, created_at, deleted_at)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                        """,
                        (id, capture_session_id, parent_recording_id, version, url, filename, created_at, deleted_at),  
                    )

                    audit_entry_creation(
                        destination_cursor,
                        table_name="recordings",
                        record_id=id,
                        record=capture_session_id,
                        created_at=created_at,
                        created_by=created_by,
                    )
                except Exception as e:  
                    self.failed_imports.add(('recordings', id, e))
                    
        log_failed_imports(self.failed_imports)
         



