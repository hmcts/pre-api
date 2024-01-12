from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports

class UserManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

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
                        created_by=user[8],
                        modified_at=user[7]
                    )
        except Exception as e:  
            self.failed_imports.add(('users', id, e))

        log_failed_imports(self.failed_imports)    

