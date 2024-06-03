from contextlib import contextmanager
import os
import sys
import logging

parent_directory = os.path.dirname(os.path.abspath(os.path.dirname(__file__)))
sys.path.append(parent_directory)
from migration.db_utils import DatabaseManager
from sql_queries import sql

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@contextmanager
def get_database_cursor(db_manager):
    cursor = db_manager.connection.cursor()
    try:
        yield cursor
    finally:
        cursor.close()

def update_audit_table_with_created_by(source_db, destination_db, table_name):
    with get_database_cursor(source_db) as source_cursor:
        try:
            source_cursor.execute(sql[table_name])
            data = source_cursor.fetchall()
            logger.info(f"Successfully fetched created_by uuids from the source database for table: {table_name}.")
        except Exception as e:
            logger.error(f"Error fetching data from source database: {e}")
            return

    with get_database_cursor(destination_db) as destination_cursor:
        try:
            update_query = """
                UPDATE public.audits
                SET created_by = %s
                WHERE table_name = %s AND table_record_id = %s
            """
            for table_id, created_by in data:
                destination_cursor.execute(update_query, (created_by, table_name, table_id))

            destination_db.connection.commit()
            logger.info(f"Successfully updated the audit table with created_by values for table name {table_name}.")
        except Exception as e:
            logger.error(f"Error updating audit table: {e}")
            destination_db.connection.rollback()

def main():
    try:
        source_db_name = os.environ['SOURCE_DB_NAME']
        source_db_user = os.environ['SOURCE_DB_USER']
        source_db_password = os.environ['SOURCE_DB_PASSWORD']
        source_db_host = os.environ['SOURCE_DB_HOST']
        destination_db_name = os.environ['DESTINATION_DB_NAME']
        destination_db_user = os.environ['DESTINATION_DB_USER']
        destination_db_password = os.environ['DESTINATION_DB_PASSWORD']
        destination_db_host = os.environ['DESTINATION_DB_HOST']
    except KeyError as e:
        logger.error(f"Missing environment variable: {e}")
        return
    
    source_db = DatabaseManager(
        database=source_db_name,
        user=source_db_user,
        password=source_db_password,
        host=source_db_host,
        port="5432"
    )
    
    destination_db = DatabaseManager(
        database=destination_db_name,
        user=destination_db_user,
        password=destination_db_password,
        host=destination_db_host,
        port="5432"
    )

    update_audit_table_with_created_by(source_db, destination_db, 'users')
    update_audit_table_with_created_by(source_db, destination_db, 'app_access')

    
if __name__ == "__main__":
    main()