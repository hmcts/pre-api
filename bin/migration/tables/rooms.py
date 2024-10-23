from .helpers import check_existing_record, audit_entry_creation, parse_to_timestamp, get_user_id
import uuid

class RoomManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute("SELECT * from public.rooms")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        batch_rooms_data = []

        for source_room in source_data:
            room = source_room[0]
            
            if not check_existing_record(destination_cursor, 'virtual_conference_rooms', 'room', room):
                id = str(uuid.uuid4())  
                created_by = source_room[1]

                batch_rooms_data.append((id, room, created_by))

        try:
            id = None
            if batch_rooms_data:   
                destination_cursor.executemany(
                    "INSERT INTO public.virtual_conference_rooms (id, room) VALUES (%s, %s)",
                    [entry[:-1] for entry in batch_rooms_data],
                )

                destination_cursor.connection.commit()

                for room in batch_rooms_data:
                    id = room[0]
                    created_at = parse_to_timestamp(room[2])
                    created_by = get_user_id(destination_cursor, room[2]) 

                    audit_entry_creation(
                        destination_cursor,
                        table_name="rooms",
                        record_id=room[0],
                        record=room[1],
                        created_at=created_at,
                        created_by= created_by if created_by is not None else None
                    )

        except Exception as e:
            self.failed_imports.append({'table_name': 'virtual_conference_rooms','table_id': id,'details': str(e)})

        self.logger.log_failed_imports(self.failed_imports)
