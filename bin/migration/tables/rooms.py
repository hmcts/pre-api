from .helpers import check_existing_record, audit_entry_creation, parse_to_timestamp, log_failed_imports
from datetime import datetime
import uuid

class RoomManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT * from public.rooms")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        batch_rooms_data = []

        for source_room in source_data:
            room = source_room[0]

            if not check_existing_record(destination_cursor, 'rooms', 'room', room):
                id = str(uuid.uuid4())  

                batch_rooms_data.append((id, room))

        try:
            if batch_rooms_data:   
                destination_cursor.executemany(
                    "INSERT INTO public.rooms (id, room) VALUES (%s, %s)",
                    batch_rooms_data
                )

                destination_cursor.connection.commit()

                for room in batch_rooms_data:
                    created_at = parse_to_timestamp(source_room[2])
                    created_by = source_room[1]

                    audit_entry_creation(
                        destination_cursor,
                        table_name="rooms",
                        record_id=room[0],
                        record=room[1],
                        created_at=created_at,
                        created_by=created_by,
                    )

        except Exception as e:
            self.failed_imports.add(('rooms', id, e))

        log_failed_imports(self.failed_imports)  