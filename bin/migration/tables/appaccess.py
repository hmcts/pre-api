from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, get_user_id 

class AppAccessManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = set()
        self.logger = logger

    def get_data(self):
        query = """ SELECT 
                        u.userid,
                        MAX(CASE WHEN gl.grouptype = 'Security' THEN ga.groupid ELSE NULL END) AS role_id,
                        MAX(CASE WHEN gl.grouptype = 'Location' THEN ga.groupid ELSE NULL END) AS court_id,
                        MAX(u.status) as active,
                        MAX(ga.assigned) AS created,
                        MAX(ga.assignedby) AS createdby,
                        MAX(ga.gaid) AS app_access_id
                    FROM public.users u
                    JOIN public.groupassignments ga ON u.userid = ga.userid
                    JOIN public.grouplist gl ON ga.groupid = gl.groupid
                    WHERE gl.groupname != 'Level 3' AND (gl.grouptype = 'Security' OR gl.grouptype = 'Location')
                    GROUP BY u.userid ;
                """
        self.source_cursor.execute(query)
        return self.source_cursor.fetchall()
    

    def migrate_data(self, destination_cursor, source_data):
        batch_app_users_data = []
        id = None

        for user in source_data:
            id=user[6]
            user_id = user[0]
            role_id = user[1]
            
            if role_id is None:
                continue

            court_id = user[2]
            active = True if user[3].lower() == "active" else False
            created_at = parse_to_timestamp(user[4])
            modified_at = created_at
            created_by = get_user_id(destination_cursor,user[5])
     
            if not check_existing_record(destination_cursor,'users', 'id', user_id):
                self.failed_imports.add(('app_access',id, f"User id not in users table: {user_id}")) 
                continue
            
            if not check_existing_record(destination_cursor,'roles', 'id', role_id):
                self.failed_imports.add(('app_access',id, f"Role: {role_id} not found in roles table for user_id: {user_id}")) 
                continue

            if court_id is None:
                self.failed_imports.add(('app_access',id, f"No court info for user_id: {user_id}")) 
                continue
            
            if not check_existing_record(destination_cursor,'courts', 'id', court_id):
                self.failed_imports.add(('app_access',id, f"Court: {court_id} not found in courts table for user_id: {user_id}")) 
                continue
            
            if not check_existing_record(destination_cursor,'app_access','user_id',user_id ):          
                # last_access = 
                batch_app_users_data.append((
                    id, user_id, court_id, role_id, active, created_at, modified_at,created_by,
                ))
                audit_entry_creation(
                    destination_cursor,
                    table_name='app_access',
                    record_id=id,
                    record=user_id,
                    created_at=created_at,
                    created_by=created_by if created_by is not None else None,
                )

        try: 
            if batch_app_users_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.app_access
                        (id, user_id, court_id, role_id, active, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s, %s,  %s)
                    """,
                    [entry[:-1] for entry in batch_app_users_data],
                )
                destination_cursor.connection.commit()

                    
        except Exception as e:  
            self.failed_imports.add(('app_access',id, e)) 

        self.logger.log_failed_imports(self.failed_imports) 
                
