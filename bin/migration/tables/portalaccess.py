from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports
from datetime import datetime
import uuid

class PortalAccessManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.users WHERE prerole = 'Level 3'")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_user_data):
        batch_portal_user_data = []

        for user in source_user_data:
            user_id = user[0]

            if not check_existing_record(destination_cursor,'portal_access', 'user_id', user_id):
                id=str(uuid.uuid4())
                password = 'password' # ?
                status = 'ACTIVE' # ? 

                last_access = datetime.now() # ?
                invitation_datetime = datetime.now() # ?
                registered_datetime = datetime.now() # ?
                status = 'ACTIVE' # ? 
                modified_at =parse_to_timestamp(user[17])
                created_by = user[14]
                created_at = parse_to_timestamp(user[15])

                batch_portal_user_data.append((
                    id, user_id, password, last_access, status, invitation_datetime, registered_datetime, created_at, modified_at, created_by
                ))

                

        try: 
            if batch_portal_user_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.portal_access
                        (id, user_id, password, last_access, status, invitation_datetime, registered_datetime, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    [entry[:-1] for entry in batch_portal_user_data],
                )

                destination_cursor.connection.commit()

                for entry in batch_portal_user_data:
                    audit_entry_creation(
                        destination_cursor,
                        table_name='portal_access',
                        record_id=entry[0],
                        record=entry[1],
                        created_at=entry[7],
                        created_by=entry[9],
                    )

        except Exception as e:
            self.failed_imports.add(('portal_access', user_id, e))
 
        log_failed_imports(self.failed_imports)
            

