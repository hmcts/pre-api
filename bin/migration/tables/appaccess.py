from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports
from datetime import datetime
import uuid

class AppAccessManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        query = """ SELECT 
                        u.userid,
                        MAX(CASE WHEN gl.grouptype = 'Security' THEN ga.groupid END) AS role_id,
                        MAX(CASE WHEN gl.grouptype = 'Location' THEN ga.groupid END) AS court_id,
                        MAX(u.status) as active,
                        MAX(ga.assigned) AS created,
                        MAX(ga.assignedby) AS createdby
                    FROM public.users u
                    JOIN public.groupassignments ga ON u.userid = ga.userid
                    JOIN public.grouplist gl ON ga.groupid = gl.groupid
                    WHERE ga.groupid != '95ebbcde-c27c-42d5-89f2-b0960350db5e'
                    GROUP BY u.userid 
                """
        self.source_cursor.execute(query)
        return self.source_cursor.fetchall()
    

    def migrate_data(self, destination_cursor, source_data):
        destination_cursor.execute("SELECT id FROM public.courts WHERE name = 'Default Court'")
        default_court_id = destination_cursor.fetchone()[0]

        batch_app_users_data = []
        id = None

        for user in source_data:
            user_id = user[0]
            role_id = user[1]
            court_id = user[2]
            active = True if user[3].lower() == "active" else False
            created_at = parse_to_timestamp(user[4])
            modified_at = created_at
            created_by = user[5]

            
            if not check_existing_record(destination_cursor,'users', 'id', user_id):
                self.failed_imports.add(('app_access',user_id, f"User id not in users table: {user_id}")) 
                continue
            
            if not check_existing_record(destination_cursor,'roles', 'id', role_id):
                self.failed_imports.add(('app_access',user_id, f"Role: {role_id} not found in roles table for user_id: {user_id}")) 
                continue

            if court_id is None:
                court_id = default_court_id
            
            if not check_existing_record(destination_cursor,'courts', 'id', court_id):
                self.failed_imports.add(('app_access',user_id, f"Court: {court_id} not found in courts table for user_id: {user_id}")) 
                continue
            
            if not check_existing_record(destination_cursor,'app_access',"user_id",user_id ):          
                id=str(uuid.uuid4())
                last_access = datetime.now() # ?
                batch_app_users_data.append((
                    id, user_id, court_id, role_id, last_access, active, created_at, modified_at,created_by,
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
                