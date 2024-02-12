from .helpers import check_existing_record, audit_entry_creation
import uuid

class RoleManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute("SELECT groupid, groupname, groupdescription from public.grouplist where grouptype = 'Security'")
        return self.source_cursor.fetchall()

    def migrate_data(self,destination_cursor, source_roles_data):
        batch_roles_data = []

        for role in source_roles_data:
            id = role[0]
            name = role[1]
            description = role[2]

            if not check_existing_record(destination_cursor,'roles', 'id', id):
                batch_roles_data.append((id, name, description))

        try:
            if batch_roles_data:
                destination_cursor.executemany(
                    'INSERT INTO public.roles (id, name, description) VALUES (%s, %s, %s)',
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
            self.failed_imports.append({'table_name': 'roles','table_id': id,'details': str(e)})
            
        self.logger.log_failed_imports(self.failed_imports)        

   