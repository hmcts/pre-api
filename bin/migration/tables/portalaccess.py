from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation
from datetime import datetime
import uuid

class PortalAccessManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor

    def get_data(self):
<<<<<<< HEAD
        self.source_cursor.execute("SELECT * FROM public.users WHERE prerole = 'Level 3' AND (sendinvite ILIKE 'true' OR invited ILIKE 'true')")
=======
        self.source_cursor.execute("SELECT * FROM public.users WHERE prerole = 'Level 3'")
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_user_data):
        batch_portal_user_data = []

        for user in source_user_data:
            user_id = user[0]

            if not check_existing_record(destination_cursor,'portal_access', 'user_id', user_id):
                id=str(uuid.uuid4())
                password = 'password' # ?
<<<<<<< HEAD
                status = 'active' # ? 
=======
                status = 'ACTIVE' # ? 
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)

                last_access = datetime.now() # ?
                invitation_datetime = datetime.now() # ?
                registered_datetime = datetime.now() # ?
                created_at = parse_to_timestamp(user[15])
                modified_at =parse_to_timestamp(user[17])
                created_by = user[14]

                batch_portal_user_data.append((
                    id, user_id, password, last_access, status, invitation_datetime, registered_datetime, created_at, modified_at
                ))

                audit_entry_creation(
                    destination_cursor,
                    table_name='portal_access',
                    record_id=id,
                    record=user_id,
                    created_at=created_at,
                    created_by=created_by,
                )

        if batch_portal_user_data:
            destination_cursor.executemany(
                """
                INSERT INTO public.portal_access
                    (id, user_id, password, last_access, status, invitation_datetime, registered_datetime, created_at, modified_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                batch_portal_user_data,
            )

            destination_cursor.connection.commit()

