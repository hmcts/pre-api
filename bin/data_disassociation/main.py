from contextlib import contextmanager
import sys
import os
import logging
import datetime

parent_directory = os.path.dirname(os.path.abspath(os.path.dirname(__file__)))
sys.path.append(parent_directory)
from migration.db_utils import DatabaseManager

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

GRACE_PERIOD_DAYS = 29

@contextmanager
def get_database_connection():
    db_name = os.getenv('DB_NAME')
    db_user = os.getenv('DB_USER')
    db_password = os.getenv('DB_PASSWORD')
    db_host = os.getenv('DB_HOST')

    if not all([db_name, db_user, db_password, db_host]):
        missing_vars = [var for var in ['DB_NAME', 'DB_USER', 'DB_PASSWORD', 'DB_HOST'] if not os.getenv(var)]
        logger.error(f"Missing environment variables: {', '.join(missing_vars)}")
        sys.exit(1)

    db_conn = DatabaseManager(
        database=db_name,
        user=db_user,
        password=db_password,
        host=db_host,
        port="5432"
    )
    try:
        yield db_conn.connection
    finally:
        db_conn.connection.close()

@contextmanager
def get_database_cursor(db_conn):
    cursor = db_conn.cursor()
    try:
        yield cursor
    finally:
        cursor.close()

def get_pending_case_closures(db_cursor):
    try:
        db_cursor.execute("SELECT id, closed_at FROM public.cases WHERE state = 'PENDING_CLOSURE';")
        return db_cursor.fetchall()
    except Exception as e:
        logger.error(f"Error fetching pending case closures: {e}")
        return []

def update_case_state_to_closed(db_conn, db_cursor, case_id):
    try:
        db_cursor.execute("UPDATE public.cases SET state = 'CLOSED' WHERE id = %s;", (case_id,))
        db_conn.commit()
        logger.info(f"case_id {case_id} state updated to CLOSED.")
    except Exception as e:
        logger.error(f"Error updating case_id {case_id} to CLOSED: {e}")

def process_pending_cases(db_conn, db_cursor,pending_cases):
    now = datetime.date.today()
    grace_period = now - datetime.timedelta(days=GRACE_PERIOD_DAYS)

    for case_id, closed_at_str in pending_cases:
        if closed_at_str:
            if closed_at_str and closed_at_str <= grace_period:
                update_case_state_to_closed(db_conn, db_cursor, case_id)
            else:
                days_remaining = (closed_at_str - grace_period).days
                logger.info(f'{case_id}: {days_remaining} days left in grace period')
        else:
            logger.warning(f"Closed_at datetime is None for case_id: {case_id}. Skipping.")

def main():
    with get_database_connection() as db_conn:
        with get_database_cursor(db_conn) as db_cursor:
            pending_cases = get_pending_case_closures(db_cursor)
            if pending_cases:
                logger.info(f"Found {len(pending_cases)} pending cases.")
                process_pending_cases(db_conn, db_cursor, pending_cases)
            else:
                logger.info("No pending cases found.")

if __name__ == "__main__":
    main()


