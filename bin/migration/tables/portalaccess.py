from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, get_user_id
import uuid

class PortalAccessManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_data(self):
        query = """ SELECT
                        u.userid,
                        u.status as active,
                        u.loginenabled as loginenabled,
                        u.invited as invited,
                        u.emailconfirmed as emailconfirmed,
                        MAX(ga.assigned) AS created,
                        MAX(ga.assignedby) AS createdby,
                        u.email
                    FROM public.users u
                    JOIN public.groupassignments ga ON u.userid = ga.userid
                    JOIN public.grouplist gl ON ga.groupid = gl.groupid
                    WHERE gl.groupname = 'Level 3' OR u.invited ILIKE 'true'
                    GROUP BY u.userid"""
        self.source_cursor.execute(query)
        return self.source_cursor.fetchall()
    
    def get_last_access_date(self,email):
        query = "SELECT MAX(auditdate) FROM audits WHERE LOWER(email) = %s"
        self.source_cursor.execute(query, (email.lower(),))
        result = self.source_cursor.fetchone()[0]
        return result if result else None

    def migrate_data(self, destination_cursor, source_user_data):
        batch_portal_user_data = []
        
        # get dataverse data
        self.source_cursor.execute("SELECT * FROM public.portal_users_dataverse")
        dataverse_data = self.source_cursor.fetchall()
        dataverse_info = {
            get_user_id(destination_cursor, dataverse_user[0]): (dataverse_user[1], dataverse_user[2])
            for dataverse_user in dataverse_data
        }

        for user in source_user_data:
            user_id = user[0]
            user_email = user[7]

            if not check_existing_record(destination_cursor,'portal_access', 'user_id', user_id):
                id = str(uuid.uuid4())
                password = 'password' # temporary field - to be removed once B2C implemented

                status_active = str(user[1]).lower() == 'active'
                
                if status_active:
                    status = "ACTIVE"
                else:
                    status = "INACTIVE"

                invited_at, registered_at = dataverse_info.get(user_id, (None, None))
                terms_accepted_at = registered_at
                last_access = parse_to_timestamp(self.get_last_access_date(user_email))
     
                created_by = get_user_id(destination_cursor, user[6])

                created_at = parse_to_timestamp(user[5])
                modified_at = created_at
                

                batch_portal_user_data.append((
                    id, user_id, password,last_access, status, created_at,invited_at, registered_at,terms_accepted_at, modified_at, created_by
                ))

        try: 
            if batch_portal_user_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.portal_access
                        (id, user_id, password, last_access, status, created_at,registered_at,terms_accepted_at, invited_at, modified_at)
                    VALUES (%s, %s, %s,%s,%s,%s,%s,%s,%s, %s)
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
                        created_at=entry[5],
                        created_by= entry[10]
                    )
        except Exception as e:
            self.failed_imports.append({'table_name': 'portal_access','table_id': id,'details': str(e)})
 
        self.logger.log_failed_imports(self.failed_imports)
