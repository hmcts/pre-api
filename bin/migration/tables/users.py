from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, get_user_id

class UserManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute("SELECT * from public.users")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_user_data):
        batch_users_data = []

        for user in source_user_data:
            id = user[0]

            if not check_existing_record(destination_cursor,'users', 'id', id):
                first_name = user[12]
                last_name = user[13]
                email = user[6]
                organisation = user[8]
                phone = user[7]
                created_at = parse_to_timestamp(user[15])
                modified_at = parse_to_timestamp(user[17])
                created_by = user[14]
                created_by = get_user_id(destination_cursor,user[14]) 

                batch_users_data.append((
                    id, first_name, last_name, email, organisation, phone, created_at, modified_at, created_by
                ))
        try:
            if batch_users_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.users 
                        (id, first_name, last_name, email, organisation, phone, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    [entry[:-1] for entry in batch_users_data],
                )
                destination_cursor.connection.commit()

                for user in batch_users_data:
                    audit_entry_creation(
                        destination_cursor,
                        table_name="users",
                        record_id=user[0],
                        record=f"{user[1]} {user[2]}",  
                        created_at=user[6],
                        created_by= created_by if created_by is not None else None
                    )
        except Exception as e:  
            self.failed_imports.append({'table_name': 'users','table_id': id,'details': str(e)})

        self.logger.log_failed_imports(self.failed_imports)    

