from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports
from datetime import datetime
import uuid

class AppAccessManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        query = """ SELECT u.* 
                    FROM public.users u, public.groupassignments ga
                    WHERE u.userid = ga.userid
                    AND u.userid NOT IN (SELECT userid FROM public.groupassignments WHERE groupid = '95ebbcde-c27c-42d5-89f2-b0960350db5e')"""
        self.source_cursor.execute(query)
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        batch_app_users_data = []
        id = None

        for user in source_data:
            user_id = user[0]

            destination_cursor.execute("SELECT id FROM public.courts WHERE name = 'Default Court'")
            default_court_id = destination_cursor.fetchone()[0]

            if not check_existing_record(destination_cursor,'app_access', 'user_id', user_id):
                id=str(uuid.uuid4())
                court_id = default_court_id  
                user_role = user[3]

                if user_role is None:
                    self.failed_imports.add(('app_access',user_id, "no role info for this user")) 
                    continue

                destination_cursor.execute("SELECT id FROM public.roles WHERE name = %s", (user[3],))
                role_id = destination_cursor.fetchone()

                last_access = datetime.now() # ?
                active = True # ?
                created_at = parse_to_timestamp(user[15])
                modified_at =parse_to_timestamp(user[17])
                created_by = user[14]

                batch_app_users_data.append((
                    id, user_id, court_id, role_id, last_access, active, created_at, modified_at, created_by
                ))

        try: 
            if batch_app_users_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.app_access
                        (id, user_id, court_id, role_id, last_access, active, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    [entry[:-1] for entry in batch_app_users_data],
                )
                destination_cursor.connection.commit()

                for entry in batch_app_users_data:
                    audit_entry_creation(
                    destination_cursor,
                    table_name='app_access',
                    record_id=entry[0],
                    record=entry[1],
                    created_at=entry[6],
                    created_by=entry[8],
                )
                    
        except Exception as e:  
            self.failed_imports.add(('app_access',user_id, e)) 

        log_failed_imports(self.failed_imports)    
                