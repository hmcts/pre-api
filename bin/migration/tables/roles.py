from .helpers import check_existing_record, audit_entry_creation
<<<<<<< HEAD

class RoleManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor

    def get_data(self):
        self.source_cursor.execute("SELECT * from public.grouplist")
        return self.source_cursor.fetchall()

    def migrate_data(self,destination_cursor, source_roles_data):
        allowed_roles = ['Level 1', 'Level 2', 'Level 3', 'Level 4', 'Super User']
        batch_roles_data = []

        for role in source_roles_data:
            id = role[0]
            name = role[1]
=======
import uuid

class RoleManager:
    def __init__(self):
        self.failed_imports = set()

    def migrate_data(self,destination_cursor):
        allowed_roles = ['Level 1', 'Level 2', 'Level 3', 'Level 4', 'Super User']
        batch_roles_data = []

        for role in allowed_roles:
            id = str(uuid.uuid4())
            name = role
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)

            if name in allowed_roles and not check_existing_record(destination_cursor,'roles', 'id', id):
                batch_roles_data.append((id, name))

        try:
            if batch_roles_data:
                destination_cursor.executemany(
                    'INSERT INTO public.roles (id, name) VALUES (%s, %s)',
                    batch_roles_data
                )

                destination_cursor.connection.commit()

                for role in batch_roles_data:
                    audit_entry_creation(
                        destination_cursor,
                        table_name='roles',
                        record_id=role[0],
                        record=role[1]
                    )
                    
        except Exception as e:
<<<<<<< HEAD
            print(f"Error during insertion: {e}")    
=======
            self.failed_imports.add(('roles', id))    

    def log_failed_imports(self, filename='failed_imports_log.txt'):
        with open(filename, 'w') as file:
            for table_name, failed_id in self.failed_imports:
                file.write(f"Table: {table_name}, ID: {failed_id}\n")
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)
