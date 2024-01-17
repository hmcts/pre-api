from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports
from datetime import datetime
import uuid

class PortalAccessManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        query = """ SELECT u.*, ga.assigned, ga.assignedby
                    FROM public.users u, public.groupassignments ga
                    WHERE u.userid = ga.userid
                    AND u.loginenabled ILIKE 'true' and u.invited ILIKE 'true'"""
        self.source_cursor.execute(query)
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_user_data):
        batch_portal_user_data = []

        for user in source_user_data:
            user_id = user[0]

            if not check_existing_record(destination_cursor,'portal_access', 'user_id', user_id):
                id=str(uuid.uuid4())
                password = 'password' # temporary field - to be removed once B2C implemented

                login_enabled = str(user[18]).lower() == 'true'
                login_disabled = str(user[18]).lower() == 'false'
                invited = str(user[19]).lower() == 'true'
                email_confirmed = str(user[11]).lower() == 'true'
                status_active = str(user[10]).lower() == 'active'
                status_inactive = str(user[10]).lower() == 'inactive'

                login_enabled_and_invited = login_enabled and invited 
                email_confirmed_and_status_inactive = email_confirmed and status_inactive
                email_confirmed_and_status_active = email_confirmed and status_active
                login_disabled_and_status_inactive = login_disabled and status_inactive 
                
                if login_enabled_and_invited and email_confirmed_and_status_inactive: 
                    status = 'REGISTERED'
                elif login_enabled_and_invited and email_confirmed_and_status_active: 
                    status = 'ACTIVE'
                elif login_enabled_and_invited: 
                    status = "INVITATION_SENT"
                elif login_disabled_and_status_inactive: 
                    status = 'INACTIVE'
                else:
                    self.failed_imports.add(('portal_access', user_id, "Missing status details"))

                last_access = parse_to_timestamp(user[17])
                invitation_datetime = parse_to_timestamp(user[16])
                registered_datetime = parse_to_timestamp(user[16])
                modified_at =parse_to_timestamp(user[20])
                created_by = user[21]
                created_at = parse_to_timestamp(user[20])

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
                        # modified_at = entry[8]
                    )

        except Exception as e:
            self.failed_imports.add(('portal_access', user_id, e))
 
        log_failed_imports(self.failed_imports)
            

