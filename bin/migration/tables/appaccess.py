from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation
from datetime import datetime
import uuid

class AppAccessManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
<<<<<<< HEAD
=======
        self.failed_imports = set()
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.users WHERE prerole != 'Level 3'")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        batch_app_users_data = []
<<<<<<< HEAD
=======
        id = None
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)

        for user in source_data:
            user_id = user[0]

            destination_cursor.execute("SELECT id FROM public.courts WHERE name = 'Default Court'")
            default_court_id = destination_cursor.fetchone()[0]

            if not check_existing_record(destination_cursor,'app_access', 'user_id', user_id):
                id=str(uuid.uuid4())
                court_id = default_court_id  

                destination_cursor.execute("SELECT id FROM public.roles WHERE name = %s", (user[3],))
                role_id = destination_cursor.fetchone()

                last_access = datetime.now() # ?
                active = True # ?
                created_at = parse_to_timestamp(user[15])
                modified_at =parse_to_timestamp(user[17])
                created_by = user[14]

                batch_app_users_data.append((
                    id, user_id, court_id, role_id, last_access, active, created_at, modified_at
                ))

                audit_entry_creation(
                    destination_cursor,
                    table_name='app_access',
                    record_id=id,
                    record=user_id,
                    created_at=created_at,
                    created_by=created_by,
                )

<<<<<<< HEAD
        if batch_app_users_data:
            destination_cursor.executemany(
                """
                INSERT INTO public.app_access
                    (id, user_id, court_id, role_id, last_access, active, created_at, modified_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                """,
                batch_app_users_data,
            )
            destination_cursor.connection.commit()
=======
            else:
                self.failed_imports.add(('app_access',id)) 

        try: 
            if batch_app_users_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.app_access
                        (id, user_id, court_id, role_id, last_access, active, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    batch_app_users_data,
                )
                destination_cursor.connection.commit()
        except Exception as e:  
            self.failed_imports.add(('app_access',id)) 
                

    def log_failed_imports(self, filename='failed_imports_log.txt'):
        with open(filename, 'w') as file:
            for table_name, failed_id in self.failed_imports:
                file.write(f"Table: {table_name}, ID: {failed_id}\n")
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)
