from .helpers import check_existing_record, parse_to_timestamp, get_user_id, audit_entry_creation

class ShareBookingsManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = set()
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.videopermissions")
        return self.source_cursor.fetchall()
    
    def get_case_id_and_version(self, recording_id):
        self.source_cursor.execute("""
                                    SELECT vp.caseuid, r.recordingversion
                                    FROM public.videopermissions vp
                                    LEFT JOIN public.cases c ON c.caseuid = vp.caseuid
                                    LEFT JOIN public.recordings r ON r.recordinguid = vp.recordinguid
                                    WHERE c.caseuid IS NOT NULL
                                    AND r.recordinguid IS NOT NULL 
                                    AND r.recordinguid = %s""", (recording_id,))
        case_data = self.source_cursor.fetchone()
        return case_data if case_data else (None, None)

    """ I am getting booking ids from different tables, because if a booking has no associated recording, 
     i won't be able to find the booking id with the recording id from the destination db """
    def get_booking_id_v1(self, destination_cursor, recording_id):
        destination_cursor.execute("SELECT booking_id FROM public.temp_recordings WHERE recording_id = %s",(recording_id,))
        booking_id = destination_cursor.fetchone()
        return booking_id[0] if booking_id else None
    
    def get_booking_id_non_v1(self, destination_cursor, recording_id):
        destination_cursor.execute("""  SELECT b.id AS booking_id
                                        FROM recordings r
                                        JOIN capture_sessions c ON c.id = r.capture_session_id
                                        JOIN bookings b ON b.id = c.booking_id
                                        WHERE r.id = %s
                                        """,(recording_id,))
        booking_id = destination_cursor.fetchone()
        return booking_id[0] if booking_id else None

    def migrate_data(self, destination_cursor, source_data):
        batch_share_bookings_data = []

        for video_permission in source_data:
            id = video_permission[0]
            recording_id = video_permission[1]
            case_id, version = self.get_case_id_and_version(recording_id)

            if case_id is None:
                self.failed_imports.add(('share_bookings',id,f"No valid case id associated with recording id: {recording_id}, not in cases table"))
                continue

            if version == '1':
                booking_id = self.get_booking_id_v1(destination_cursor, recording_id)
            else:
                booking_id = self.get_booking_id_non_v1(destination_cursor, recording_id)
            
            if booking_id is None:
                self.failed_imports.add(('share_bookings',id,f"No valid booking id associated with case id: {case_id} (recording id: {recording_id}) not in bookings table"))
                continue
            
            shared_with_user_id = video_permission[4]
            if not check_existing_record(destination_cursor,'users','id',shared_with_user_id):
                self.failed_imports.add(('share_bookings', id, f"Invalid shared_with_user_id value: {shared_with_user_id} (recording id: {recording_id})"))
                continue

            created_by = get_user_id(destination_cursor,video_permission[18])
            shared_by_user_id = created_by

            if not shared_by_user_id:
                self.failed_imports.add(('share_bookings', id, f"No user found for shared_with_user email : {created_by} (recording id: {recording_id})"))
                continue

            created_at = parse_to_timestamp(video_permission[19])
            deleted_at = parse_to_timestamp(video_permission[21]) if video_permission[15] != "True" else None       

            if not check_existing_record(destination_cursor, 'share_bookings','id',id):
                batch_share_bookings_data.append((id, booking_id, shared_with_user_id, shared_by_user_id,created_at, deleted_at))

                audit_entry_creation(
                        destination_cursor,
                        table_name="share_bookings",
                        record_id=id,
                        record=booking_id,
                        created_at=created_at,
                        created_by=created_by                    
                    )
            
        try:
            if batch_share_bookings_data:
                 destination_cursor.executemany(
                    """
                    INSERT INTO public.share_bookings
                        (id, booking_id, shared_with_user_id, shared_by_user_id,created_at, deleted_at)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    batch_share_bookings_data,
                )
            destination_cursor.connection.commit()

        except Exception as e:
            destination_cursor.connection.rollback()    
            self.failed_imports.add(('share_bookings', id, e))

        self.logger.log_failed_imports(self.failed_imports)
        

