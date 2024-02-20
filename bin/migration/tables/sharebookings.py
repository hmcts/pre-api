from .helpers import check_existing_record, parse_to_timestamp, get_user_id, audit_entry_creation


class ShareBookingsManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
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

    def check_existing_shared_booking(self, destination_cursor, booking_id, shared_by_user_id, shared_with_user_id):
        query = """
            SELECT EXISTS (
                SELECT 1 FROM public.share_bookings 
                WHERE booking_id = %s AND shared_by_user_id = %s AND shared_with_user_id = %s
            )
        """
        destination_cursor.execute(
            query, (booking_id, shared_by_user_id, shared_with_user_id))
        result = destination_cursor.fetchone()
        if result is not None:
            return result[0]
        else:
            return False

    """ I am getting booking ids from different tables, because if a booking has no associated recording, 
     i won't be able to find the booking id with the recording id from the destination db """

    def get_booking_id_v1(self, destination_cursor, recording_id):
        destination_cursor.execute(
            "SELECT booking_id FROM public.temp_recordings WHERE recording_id = %s", (recording_id,))
        booking_id = destination_cursor.fetchone()
        return booking_id[0] if booking_id else None

    def get_booking_id_non_v1(self, destination_cursor, recording_id):
        destination_cursor.execute("""  SELECT b.id AS booking_id
                                        FROM recordings r
                                        JOIN capture_sessions c ON c.id = r.capture_session_id
                                        JOIN bookings b ON b.id = c.booking_id
                                        WHERE r.id = %s
                                        """, (recording_id,))
        booking_id = destination_cursor.fetchone()
        return booking_id[0] if booking_id else None

    def migrate_data(self, destination_cursor, source_data):
        for video_permission in source_data:
            id = video_permission[0]
            recording_id = video_permission[1]
            case_id, version = self.get_case_id_and_version(recording_id)

            if case_id is None:
                self.failed_imports.append({
                    'table_name': 'share_bookings',
                    'table_id': id,
                    'case_id': case_id,
                    'recording_id': recording_id,
                    'details':  f"No valid Case ID associated with recording in cases table."
                })
                continue

            if version == '1':
                booking_id = self.get_booking_id_v1(
                    destination_cursor, recording_id)
            else:
                booking_id = self.get_booking_id_non_v1(
                    destination_cursor, recording_id)

            if booking_id is None:
                self.failed_imports.append({
                    'table_name': 'share_bookings',
                    'table_id': id,
                    'recording_id': recording_id,
                    'case_id': case_id,
                    'details':  f"No valid Booking ID associated recording."
                })
                continue

            shared_with_user_id = video_permission[4]
            if not check_existing_record(destination_cursor, 'users', 'id', shared_with_user_id):
                self.failed_imports.append({
                    'table_name': 'share_bookings',
                    'table_id': id,
                    'recording_id': recording_id,
                    'case_id': case_id,
                    'details':  f"Invalid shared_with_user_id value: {shared_with_user_id} in users table."
                })
                continue

            email = video_permission[18]
            created_by = get_user_id(destination_cursor, email)
            shared_by_user_id = created_by

            if not shared_by_user_id:
                self.failed_imports.append({
                    'table_name': 'share_bookings',
                    'table_id': id,
                    'case_id': case_id,
                    'recording_id': recording_id,
                    'details': f"shared_by_user email: {email} not found in users table."
                })
                continue

            created_at = parse_to_timestamp(video_permission[19])
            deleted_at = parse_to_timestamp(
                video_permission[21]) if video_permission[15] != "True" else None

            if not check_existing_record(destination_cursor, 'share_bookings', 'id', id):
                if self.check_existing_shared_booking(destination_cursor, booking_id, shared_by_user_id, shared_with_user_id):
                    self.failed_imports.append({
                        'table_name': 'share_bookings',
                        'table_id': id,
                        'case_id': case_id,
                        'recording_id': recording_id,
                        'details': f"Shared recording already migrated for booking_id: {booking_id}, shared_with: {shared_with_user_id}, shared_by: {shared_by_user_id}"
                    })
                    continue
                try:
                    destination_cursor.execute(
                        """
                            INSERT INTO public.share_bookings
                                (id, booking_id, shared_with_user_id, shared_by_user_id,created_at, deleted_at)
                            VALUES (%s, %s, %s, %s, %s, %s)
                            """,
                        (id, booking_id, shared_with_user_id,
                         shared_by_user_id, created_at, deleted_at),
                    )
                except Exception as e:
                    destination_cursor.connection.rollback()
                    self.failed_imports.append({
                        'table_name': 'share_bookings',
                        'table_id': id,
                        'case_id': case_id,
                        'recording_id': recording_id,
                        'details': str(e)
                    })

                audit_entry_creation(
                    destination_cursor,
                    table_name="share_bookings",
                    record_id=id,
                    record=booking_id,
                    created_at=created_at,
                    created_by=created_by
                )

        self.logger.log_failed_imports(self.failed_imports)
