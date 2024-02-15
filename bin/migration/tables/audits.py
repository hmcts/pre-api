from .helpers import check_existing_record, parse_to_timestamp, get_user_id 
import uuid
import json

class AuditLogManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute("SELECT * from public.audits")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        batch_audit_data = []

        for audit_log in source_data:
            table_name =  "audits"
            table_record_id = audit_log[0]

            if not check_existing_record(destination_cursor,'audits', 'table_record_id', table_record_id):
                id = str(uuid.uuid4())
                source = "AUTO"
                category =audit_log[20]
                activity = audit_log[2]
                functional_area = audit_log[17]

                audit_details_dict = {
                    "description": audit_log[5],
                    "subfunctionalArea": audit_log[18],
                    "trigger": audit_log[19],
                    "recordingId": audit_log[3],
                    "email": audit_log[6],
                    "caseId": audit_log[4],
                    "caseReference": audit_log[8],
                    "courtName": audit_log[9],
                    "source": audit_log[16],
                    "auditSession":audit_log[7]
                }
                audit_details_json = json.dumps(audit_details_dict)
                audit_details = audit_details_json
                created_at = parse_to_timestamp(audit_log[12])

                if audit_log[11]:
                    created_by_id = get_user_id(destination_cursor,audit_log[11])
               
                created_by = created_by_id if created_by_id is not None else None

                batch_audit_data.append((
                        id, table_name, table_record_id, source, category, activity, functional_area, audit_details, created_at, created_by))
        try:
            if batch_audit_data:
                destination_cursor.executemany(
                    """INSERT INTO public.audits (id, table_name, table_record_id, source, category, activity, functional_area, audit_details, created_at, created_by)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
                    batch_audit_data
                )
                destination_cursor.connection.commit()  
            
        except Exception as e:
            destination_cursor.connection.rollback() 
            self.failed_imports.append({'table_name': 'audits','table_id': id,'details': str(e)})

        self.logger.log_failed_imports(self.failed_imports)

