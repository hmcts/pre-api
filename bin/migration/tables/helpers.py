from datetime import datetime
import pytz
import uuid
import json
from migration_reports.failed_imports_logger import FailedImportsLogger

# Parses timestamp string to date format
def parse_to_timestamp(input_text):
    if input_text:
        try:
            parsed_datetime = None
            formats_to_try = [
                "%d/%m/%Y",
                "%d/%m/%Y %H:%M",
                "%d/%m/%Y %H:%M:%S",
                "%Y/%m/%d %H:%M:%S",
                "%Y/%m/%d %H:%M",
                "%d-%m-%Y %H:%M:%S",
                "%d-%m-%Y %H:%M",
                "%Y-%m-%d %H:%M:%S",
                ]
            for date_format in formats_to_try:
                try:
                    parsed_datetime = datetime.strptime(input_text, date_format)
                    break
                except ValueError:
                    pass

            if parsed_datetime:
                uk_timezone = pytz.timezone('Europe/London')
                parsed_datetime = uk_timezone.localize(parsed_datetime)
                return parsed_datetime

        except (ValueError, TypeError) as e:
            pass

# Checks if record is already imported
def check_existing_record(db_connection, table_name, field, record):
    query = f"SELECT EXISTS (SELECT 1 FROM public.{table_name} WHERE {field} = %s)"
    db_connection.execute(query, (record,))
    return db_connection.fetchone()[0]

# Audit entry into database
def audit_entry_creation(db_connection, table_name, record_id, record, created_at = None,created_by = None, logger = None ):
    logger = FailedImportsLogger()
    created_at = created_at if created_at is not None else datetime.now()
    created_by = created_by if created_by is not None else None

    audit_details_dict = {"description": f"Created {table_name}_record for: {record}"},
    audit_details_json = json.dumps(audit_details_dict)

    audit_entry = {
        "id": str(uuid.uuid4()),
        "table_name": table_name,
        "table_record_id": record_id,
        "source": "AUTO",
        "category": "data_migration",
        "activity": f"{table_name}_record_creation",
        "functional_area": "data_processing",
        "audit_details" : audit_details_json,
        "created_by": created_by,
        "created_at": created_at,
    }

    try:
        db_connection.execute(
            """
            INSERT INTO public.audits
                (id, table_name, table_record_id, source, category, activity, functional_area, audit_details, created_by, created_at)
            VALUES
                (%(id)s, %(table_name)s, %(table_record_id)s, %(source)s, %(category)s, %(activity)s, %(functional_area)s, %(audit_details)s, %(created_by)s, %(created_at)s)
            """,
            audit_entry
        )
        db_connection.connection.commit()

    except Exception as e:
        logger.log_failed_imports([{
            'table_name': 'audits',
            'table_id': record_id,
            'details': str(e)
        }])

# Get the user_id associated with an email from the users table for the audits record.
def get_user_id(db_connection, email):
    if email is None:
        return None

    db_connection.execute("""
            SELECT id
            FROM public.users
            WHERE email = %s
            """, (email.lower(),))
    result = db_connection.fetchone()

    if result is not None:
        user_id = result[0]
        return user_id
    else:
        return None



