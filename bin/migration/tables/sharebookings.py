from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports
# --CREATE TABLE public.share_bookings (
# --    id UUID PRIMARY KEY,
# --    booking_id UUID REFERENCES bookings(id) NOT NULL,
# --    shared_with_user_id UUID REFERENCES users(id) NOT NULL,
# --    shared_by_user_id UUID REFERENCES users(id) NOT NULL,
# --    created_at TIMESTAMPTZ DEFAULT NOW(),
# --    deleted_at TIMESTAMPTZ DEFAULT NULL

class ShareBookingsManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.videopermissions")
        return self.source_cursor.fetchall()
    
    def get_booking(self, bookings_data, recording_id):
        booking_id = next((booking[0] for booking in bookings_data if booking[1] == recording_id), None)
        return booking_id
    
    def get_user(self, users_data,  user_email):
        user_id = next((user[0] for user in users_data if user[3] == user_email), None)
        return user_id

    def migrate_data(self, destination_cursor, source_data):
        batch_share_bookings_data = []

        destination_cursor.execute("""  SELECT b.id AS booking_id, r.id AS recording_id
                                        FROM bookings b
                                        LEFT JOIN capture_sessions cs ON cs.booking_id = b.id
                                        LEFT JOIN recordings r ON r.capture_session_id = cs.id
                                        WHERE r.id is not null""")
        bookings_data = destination_cursor.fetchall()

        destination_cursor.execute("SELECT * FROM public.users")
        users_data = destination_cursor.fetchall()

        for video_permission in source_data:
            id = video_permission[0]
            recording_id = video_permission[1]

            booking_id = self.get_booking(bookings_data, recording_id)

            shared_with_user_id = video_permission[4]

            created_by = video_permission[18]
            shared_by_user_id = self.get_user(users_data, created_by)

            created_at = parse_to_timestamp(video_permission[19])
            deleted_at = parse_to_timestamp(video_permission[21]) if video_permission[15] != "True" else None

            if not booking_id:
                self.failed_imports.add(('share_bookings', id, f"No booking id found for recordinguid: {recording_id}"))
                continue

            if not check_existing_record(destination_cursor,'users','id',shared_with_user_id):
                self.failed_imports.add(('share_bookings', id, f"Invalid shared_with_user_id value: {shared_with_user_id}"))
                continue

            if not shared_by_user_id:
                self.failed_imports.add(('share_bookings', id, f"No user found for shared_with_user email : {created_by}"))
                continue

            batch_share_bookings_data.append((id, booking_id, shared_with_user_id, shared_by_user_id,created_at, deleted_at))
            
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

            for entry in batch_share_bookings_data:
                audit_entry_creation(
                    destination_cursor,
                    table_name="share_bookings",
                    record_id=entry[0],
                    record=entry[1],
                    created_at=entry[4],
                    created_by=entry[3],
                )

        except Exception as e:
            self.failed_imports.add(('share_bookings', id, e))

        log_failed_imports(self.failed_imports)
        

