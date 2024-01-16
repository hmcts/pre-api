from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports
from datetime import datetime
import uuid

class AppAccessManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        query = """
            SELECT u.*, gl.groupname, ga.assigned, ga.assignedby
            FROM public.users u
            JOIN public.groupassignments ga ON u.userid = ga.userid
            JOIN public.grouplist gl ON ga.groupid = gl.groupid
            WHERE u.userid NOT IN (
                SELECT userid
                FROM public.groupassignments
                WHERE groupid = '95ebbcde-c27c-42d5-89f2-b0960350db5e'
            )
        """
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
                user_role = user[20]

                if user_role is None:
                    self.failed_imports.add(('app_access',user_id, f"No role info for user_id {user_id}")) 
                    continue

                destination_cursor.execute("SELECT id FROM public.roles WHERE name = %s", (user_role,))
                role_id = destination_cursor.fetchone()

                if role_id is None:
                    self.failed_imports.add(('app_access',user_id, f"Role not listed in roles table {user_role}"))
                    continue

                last_access = datetime.now() # ?
                
                if str(user[10]).lower() == 'active':
                    active = True
                elif str(user[10]).lower() == 'inactive':
                    active = False
                created_at = parse_to_timestamp(user[21])
                modified_at =parse_to_timestamp(user[21]) 
                created_by = user[22]

                batch_app_users_data.append((
                    id, user_id, court_id, role_id, last_access, active, created_at, created_by, modified_at
                ))

        try: 
            if batch_app_users_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.app_access
                        (id, user_id, court_id, role_id, last_access, active, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
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
                    modified_at=entry[7]
                )
                    
        except Exception as e:  
            self.failed_imports.add(('app_access',user_id, e)) 

        log_failed_imports(self.failed_imports)    
                