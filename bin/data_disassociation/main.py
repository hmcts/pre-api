from contextlib import contextmanager
import sys
import os
import logging
import datetime
import uuid
import json

parent_directory = os.path.dirname(os.path.abspath(os.path.dirname(__file__)))
sys.path.append(parent_directory)
from migration.db_utils import DatabaseManager

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

GRACE_PERIOD_DAYS = 29

def establish_database_connection():
    """ Establishes a db connection using credentials from env vars."""
    db_name = os.getenv('DB_NAME')
    db_user = os.getenv('DB_USER')
    db_password = os.getenv('DB_PASSWORD')
    db_host = os.getenv('DB_HOST')

    if not all([db_name, db_user, db_password, db_host]):
        missing_vars = [var for var in ['DB_NAME', 'DB_USER', 'DB_PASSWORD', 'DB_HOST'] if not os.getenv(var)]
        logger.error(f"Missing environment variables: {', '.join(missing_vars)}")
        raise ValueError("Missing database configuration")

    return DatabaseManager(database=db_name, user=db_user, password=db_password, host=db_host, port="5432").connection

@contextmanager
def get_database_connection():
    """ Context manager for database connection."""
    db_conn = None
    try:
        db_conn = establish_database_connection()
        yield db_conn
    finally:
        if db_conn:
            db_conn.close()

@contextmanager
def get_database_cursor(db_conn):
    """ Context manager for database cursor."""
    cursor = db_conn.cursor()
    try:
        yield cursor
    finally:
        cursor.close()

# Database Operations
def fetch_all(db_cursor, query, params=()):
    """Executes a fetch all query."""
    try:
        db_cursor.execute(query, params)
        return db_cursor.fetchall()
    except Exception as e:
        logger.error(f"Error executing query: {e}")
        return []
    
def execute_update(db_conn, db_cursor, query, params):
    """ Executes an update query."""
    try:
        db_cursor.execute(query, params)
        db_conn.commit()
        return True
    except Exception as e:
        logger.error(f"Error executing update: {e}")
        return False

def make_audit_entry(db_conn, db_cursor, table_name, record_id, operation):
    """ Creates an audit entry."""
    audit_query = """
        INSERT INTO audits (id, table_name, table_record_id, source, category, activity, functional_area, created_at, audit_details)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s);
    """
    audit_id = str(uuid.uuid4())
    source = "APPLICATION" 
    category = "UPDATE"  
    activity = operation
    functional_area = "Case Management" 
    created_at = datetime.datetime.now()
    audit_details = json.dumps({"operation": operation, "table_name": table_name, "record_id": record_id})

    execute_update(db_conn, db_cursor, audit_query, (audit_id,table_name, record_id,source, category, activity, functional_area, created_at, audit_details))
    logger.info(f"Audit entry created for {operation} on {table_name} id {record_id}.")


# Logic
def get_pending_case_closures(db_cursor):
    """Fetches pending case closures."""
    query = "SELECT id, closed_at FROM public.cases WHERE state = 'PENDING_CLOSURE';"
    return fetch_all(db_cursor, query)

def update_case_state_to_closed(db_conn, db_cursor, case_id):
    """Updates case state to CLOSED."""
    update_query = "UPDATE public.cases SET state = 'CLOSED' WHERE id = %s;"
    if execute_update(db_conn, db_cursor, update_query, (case_id,)):
        logger.info(f"case id: {case_id} state updated to CLOSED.")
        make_audit_entry(db_conn, db_cursor, 'cases', case_id, 'UPDATE')

def get_shared_bookings(db_cursor, case_id):
    """Fetches shared bookings for a case."""
    shared_booking_query = """
        SELECT sb.id 
        FROM public.share_bookings sb
        JOIN public.bookings b ON sb.booking_id = b.id
        JOIN public.cases c ON c.id = b.case_id
        WHERE c.id = %s;
    """
    return fetch_all(db_cursor, shared_booking_query, (case_id,))

def update_share_bookings(db_conn, db_cursor, share_bookings):
    """Updates shared bookings with deleted_at timestamp."""
    for share_booking_id, in share_bookings:
        update_query = "UPDATE public.share_bookings SET deleted_at = NOW() WHERE id = %s;"
        if execute_update(db_conn, db_cursor, update_query, (share_booking_id,)):
            logger.info(f"share_booking id: {share_booking_id} updated with deleted_at timestamp.")
            make_audit_entry(db_conn, db_cursor, 'share_bookings', share_booking_id, 'UPDATE')

def process_pending_cases(db_conn, db_cursor,pending_cases):
    """Processes pending cases and updates case state and shared bookings."""
    now = datetime.date.today()
    grace_period = now - datetime.timedelta(days=GRACE_PERIOD_DAYS)

    for case_id, closed_at in pending_cases:
        if closed_at: 
            if closed_at and closed_at <= grace_period:
                update_case_state_to_closed(db_conn, db_cursor, case_id)
                shared_bookings = get_shared_bookings(db_cursor, case_id)
                if shared_bookings:
                        update_share_bookings(db_conn, db_cursor, shared_bookings)
        else:
            logger.warning(f"Closed_at datetime is None for case_id: {case_id}. Skipping.")

def main():
    try:
        with get_database_connection() as db_conn:
            with get_database_cursor(db_conn) as db_cursor:
                pending_cases = get_pending_case_closures(db_cursor)
                if pending_cases:
                    logger.info(f"Found {len(pending_cases)} pending cases.")
                    process_pending_cases(db_conn, db_cursor, pending_cases)
                else:
                    logger.info("No pending cases found.")
    except Exception as e:
        logger.error(f"Pipeline execution failed: {str(e)}")

if __name__ == "__main__":
    main()


