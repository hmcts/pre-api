from datetime import datetime
import pytz
import uuid


# Parses timestamp string to date format
def parse_to_timestamp(input_text):
    failed_imports = set()
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
            failed_imports.add(('date/time', input_text, e))
            log_failed_imports(failed_imports)


# Checks if record is already imported
def check_existing_record(db_connection, table_name, field, record):
    query = f"SELECT EXISTS (SELECT 1 FROM public.{table_name} WHERE {field} = %s)"
    db_connection.execute(query, (record,))
    return db_connection.fetchone()[0]


# Audit entry into database
def audit_entry_creation(db_connection, table_name, record_id, record, created_at=None, created_by="Data Entry"):
    created_at = created_at or datetime.now()

    failed_imports = set()

    audit_entry = {
        "id": str(uuid.uuid4()),
        "source": "AUTO",
        "category": "data_migration",
        "activity": f"{table_name}_record_creation",
        "functional_area": "data_processing",
        "audit_details": f'\{"description": "Created {table_name}_record for: {record}"\}',
        "created_by": created_by,
        "created_at": created_at,
    }

    try:
        db_connection.execute(
            """
            INSERT INTO public.audits
                (id, table_name, table_record_id, source, category, activity, functional_area, audit_details, created_by, created_at)
            VALUES
                (%(id)s, %(table_name)s, %(table_record_id)s, %(source)s, %(type)s, %(category)s, %(activity)s, %(functional_area)s, %(audit_details)s, %(created_by)s, %(created_at)s)
            """,
            audit_entry
        )
        db_connection.connection.commit()

    except Exception as e:
        failed_imports.add(('audit table', table_name, e))
        log_failed_imports(failed_imports)

# Logs failed imports to file
def log_failed_imports(failed_imports, filename='failed_imports_log.txt'):
    with open(filename, 'a') as file:
        for entry in failed_imports:
            if len(entry) == 2:
                table_name, failed_id = entry
                details = 'Import failed'
            elif len(entry) == 3:
                table_name, failed_id, details = entry
            else:
                raise ValueError("Each entry in failed_imports should have 2 or 3 elements")

            file.write(f"Table: {table_name}, ID: {failed_id}, Details: {details}\n")


# Clear the migration file - run before the migration script is run
def clear_migrations_file(filename='failed_imports_log.txt'):
    with open(filename, 'w') as file:
        file.write("")

# Get the user_id associated with an email from the users table for the audits record.
def get_user_id(db_connection, email):
    db_connection.execute("""
            SELECT id
            FROM public.users
            WHERE email = %s
            """, (email,))
    result =  db_connection.fetchone()

    if result is not None:
        user_id = result[0]
        return user_id
    else:
        return None
