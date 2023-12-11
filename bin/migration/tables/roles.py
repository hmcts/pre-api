from .helpers import check_existing_record, audit_entry_creation, log_failed_imports
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
            self.failed_imports.add(('roles', id,e ))
            
        log_failed_imports(self.failed_imports)        

   