from datetime import datetime
import pytz
import uuid

# parse date string to timestamp UTC value
def parse_to_timestamp(input_text):
    if input_text:
        try:
            parsed_datetime = None
            # try parsing with different formats
            formats_to_try = ["%d/%m/%Y %H:%M", "%d/%m/%Y %H:%M:%S", "%d-%m-%Y %H:%M:%S"]
            for date_format in formats_to_try:
                try:
                    parsed_datetime = datetime.strptime(input_text, date_format)
                    break 
                except ValueError:
                    pass

            if parsed_datetime:
                uk_timezone = pytz.timezone('Europe/London')
                parsed_datetime = uk_timezone.localize(parsed_datetime)
                return parsed_datetime.strftime('%Y-%m-%d %H:%M:%S')
        except (ValueError, TypeError):
            pass
    # if input is invalid or empty, returning the current time in UK timezone
    return datetime.now(tz=pytz.timezone('Europe/London')).strftime('%Y-%m-%d %H:%M:%S')




# checks if a record has already been imported
def check_existing_record(db_connection, table_name, field, record):
    query = f"SELECT EXISTS (SELECT 1 FROM public.{table_name} WHERE {field} = %s)"
    db_connection.execute(query, (record,))
    return db_connection.fetchone()[0]



# audit entry into database
def audit_entry_creation(db_connection, table_name, record_id, record, created_at=None, created_by="Data Entry"):
    created_at = created_at or datetime.now()

    audit_entry = {
        "id": str(uuid.uuid4()),
        "table_name": table_name,
        "table_record_id": record_id,
<<<<<<< HEAD
        "source": "auto",
        "type": "create",
=======
        "source": "AUTO",
        "type": "CREATE",
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)
        "category": "data_migration",
        "activity": f"{table_name}_record_creation",
        "functional_area": "data_processing",
        "audit_details": f"Created {table_name}_record for: {record}",
        "created_by": created_by,
        "created_at": created_at
    }

    try:
        db_connection.execute(
            """
            INSERT INTO public.audits 
                (id, table_name, table_record_id, source, type, category, activity, functional_area, audit_details, created_by, created_at) 
            VALUES 
                (%(id)s, %(table_name)s, %(table_record_id)s, %(source)s, %(type)s, %(category)s, %(activity)s, %(functional_area)s, %(audit_details)s, %(created_by)s, %(created_at)s)
            """,
            audit_entry
        )

        db_connection.connection.commit()
        
    except Exception as e:
        print(f"Error during insertion: {e}")
