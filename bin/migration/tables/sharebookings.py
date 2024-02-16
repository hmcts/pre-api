from .helpers import check_existing_record, parse_to_timestamp, get_user_id

class ShareBookingsManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.videopermissions")
        return self.source_cursor.fetchall()
    
    def get_booking(self, bookings_data, recording_id):
        booking_id = next((booking[0] for booking in bookings_data if booking[1] == recording_id), None)
        return booking_id
    

    def migrate_data(self, destination_cursor, source_data):
        batch_share_bookings_data = []

        destination_cursor.execute("""  SELECT b.id AS booking_id, r.id AS recording_id
                                        FROM bookings b
                                        LEFT JOIN capture_sessions cs ON cs.booking_id = b.id
                                        LEFT JOIN recordings r ON r.capture_session_id = cs.id
                                        WHERE r.id is not null""")
        bookings_data = destination_cursor.fetchall()

        for video_permission in source_data:
            id = video_permission[0]
            recording_id = video_permission[1]

            booking_id = self.get_booking(bookings_data, recording_id)

            shared_with_user_id = video_permission[4]

            email = get_user_id(destination_cursor,video_permission[8])
            shared_by_user_id = email

            created_at = parse_to_timestamp(video_permission[19])
            deleted_at = parse_to_timestamp(video_permission[21]) if video_permission[15] != "True" else None

            if not recording_id:
                self.failed_imports.append({
                    'table_name': 'share_bookings',
                    'table_id': id,
                    'recording_id': recording_id,
                    'details':  f"No Recording ID found for Video permission record: {id}."
                })
                continue

            
            if not booking_id:
                self.failed_imports.append({
                    'table_name': 'share_bookings',
                    'table_id': id,
                    'recording_id': recording_id,
                    'details':  f"No Booking ID found for Recording ID: {recording_id}"
                })
                continue

            if not check_existing_record(destination_cursor,'users','id',shared_with_user_id):
                self.failed_imports.append({
                    'table_name': 'share_bookings',
                    'table_id': id,
                    'recording_id': recording_id,
                    'details': f"shared_with_user_id value: {shared_with_user_id} not found in Users table."
                })
                continue

            if not shared_by_user_id:
                self.failed_imports.append({
                    'table_name': 'share_bookings',
                    'table_id': id,
                    'recording_id': recording_id,
                    'details': f"shared_by_user email: {email} not found in users table."
                })
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

        except Exception as e:
            destination_cursor.connection.rollback()    
            self.failed_imports.append({'table_name': 'share_bookings','table_id': id,'details': str(e)})


        self.logger.log_failed_imports(self.failed_imports)
        

