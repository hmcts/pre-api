from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, get_user_id
import uuid

class PortalAccessManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = set()
        self.logger = logger

    def get_data(self):
        query = """ SELECT
                        u.userid,
                        u.status as active,
                        u.loginenabled as loginenabled,
                        u.invited as invited,
                        u.emailconfirmed as emailconfirmed,
                        MAX(ga.assigned) AS created,
                        MAX(ga.assignedby) AS createdby
                    FROM public.users u
                    JOIN public.groupassignments ga ON u.userid = ga.userid
                    JOIN public.grouplist gl ON ga.groupid = gl.groupid
                    WHERE gl.groupname = 'Level 3' OR u.invited ILIKE 'true'
                    GROUP BY u.userid"""
        self.source_cursor.execute(query)
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_user_data):
        batch_portal_user_data = []

        for user in source_user_data:
            user_id = user[0]

            if not check_existing_record(destination_cursor,'portal_access', 'user_id', user_id):
                id = str(uuid.uuid4())
                password = 'password' # temporary field - to be removed once B2C implemented
                status = 'INVITATION_SENT'

                login_enabled = str(user[2]).lower() == 'true'
                login_disabled = str(user[2]).lower() == 'false'
                email_confirmed = str(user[4]).lower() == 'true'
                status_active = str(user[1]).lower() == 'active'
                invited = True
                status_inactive = str(user[1]).lower() == 'inactive'

                login_enabled_and_invited = login_enabled and invited
                email_confirmed_and_status_inactive = email_confirmed and status_inactive
                email_confirmed_and_status_active = email_confirmed and status_active
                login_disabled_and_status_inactive = login_disabled and status_inactive

                if status_inactive or login_disabled_and_status_inactive:
                    status = "INACTIVE"
                elif login_enabled_and_invited and email_confirmed_and_status_inactive:
                    status = 'REGISTERED'
                elif login_enabled_and_invited and email_confirmed_and_status_active:
                    status = 'ACTIVE'
                elif login_enabled_and_invited:
                    status = "INVITATION_SENT"
                else:
                    status = "INVITATION_SENT"

                # last_access = datetime.now() # this value is obtained from DV
                # invited_at = parse_to_timestamp(user[5]) # this value is obtained from DV
                # registered_at = parse_to_timestamp(user[5]) # this value is obtained from DV
                created_by = get_user_id(destination_cursor, user[6])

                created_at = parse_to_timestamp(user[5])
                modified_at = created_at

                batch_portal_user_data.append((
                    id, user_id, password, status, created_at, modified_at, created_by
                ))

        try: 
            if batch_portal_user_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.portal_access
                        (id, user_id, password, status, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s, %s)
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
                        created_at=entry[4],
                        created_by= entry[6]
                    )
        except Exception as e:
            self.failed_imports.add(('portal_access', user_id, e))
 
        self.logger.log_failed_imports(self.failed_imports)
