import os
import uuid
import logging
import csv
from contextlib import contextmanager
import sys
from datetime import datetime

parent_directory = os.path.dirname(os.path.abspath(os.path.dirname(__file__)))
sys.path.append(parent_directory)
from migration.db_utils import DatabaseManager

from courts_data import court_data_dictionary

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@contextmanager
def get_database_cursor(db_manager):
    cursor = db_manager.connection.cursor()
    try:
        yield cursor
    finally:
        cursor.close()

def fetch_court_code(cursor, court_name):
    cursor.execute('SELECT * FROM courts WHERE name = %s', (court_name,))
    return cursor.fetchone()

def update_location_code(cursor, court_name, location_code):
    cursor.execute('UPDATE courts SET location_code = %s WHERE name = %s', (location_code, court_name))

def insert_court(cursor, court_name, court_type, location_code):
    id = str(uuid.uuid4())
    cursor.execute('INSERT INTO courts (id, name, court_type, location_code) VALUES (%s, %s, %s, %s)', 
                   (id, court_name, court_type, location_code))

def upsert_court_info(cursor, location_code, court_data):
    court_name = court_data["name"]
    existing_record = fetch_court_code(cursor, court_name)

    if existing_record:
        id, court_type, _, existing_location_code  = existing_record
        if existing_location_code != str(location_code) :
            update_location_code(cursor, court_name, location_code)
            return court_name, id, location_code, court_type, True, False, f"Location code changed from {existing_location_code} to {location_code}"
        return court_name, id, location_code, court_type, False, False, "No change"
    else:
        insert_court(cursor, court_name, court_data["type"], location_code)
        id = str(uuid.uuid4())
        return court_name, id, location_code, court_data["type"], False, True, "New court"
   

def generate_csv_report(updated_courts, not_updated_courts):
    csv_output = "report.csv"
    with open(csv_output, mode='w', newline='') as file:
        writer = csv.writer(file)

        writer.writerow(["Report - " + datetime.now().strftime('%Y-%m-%d %H:%M:%S')])
        writer.writerow(["Court Name", "ID", "Location Code", "Type", "Updated", "New Court", "Change"])

        writer.writerow([f"Courts updated ({len(updated_courts)}):"])
        for court in updated_courts:
            writer.writerow([court[0], court[1], court[2], court[3], "Yes" if court[4] else "No", 
                             "Yes" if court[5] else "No", court[6]])


        writer.writerow([])  
        writer.writerow([f"Courts not updated ({len(not_updated_courts)}):"])
        for court in not_updated_courts:
             writer.writerow([court[0], court[1], court[2], court[3], "Yes" if court[4] else "No", 
                             "Yes" if court[5] else "No", court[6]])

    print(f"CSV report generated: {csv_output}")

def main():
    try:
        destination_db_name = os.environ['DESTINATION_DB_NAME']
        destination_db_user = os.environ['DESTINATION_DB_USER']
        destination_db_password = os.environ['DESTINATION_DB_PASSWORD']
        destination_db_host = os.environ['DESTINATION_DB_HOST']
    except KeyError as e:
        logger.error(f"Missing environment variable: {e}")
        return

    destination_db = DatabaseManager(
        database=destination_db_name,
        user=destination_db_user,
        password=destination_db_password,
        host=destination_db_host,
        port="5432"
    )

    updated_courts = []
    not_updated_courts = []

    try:
        with get_database_cursor(destination_db) as cursor:
            for location_code, court_data in court_data_dictionary.items():
                try:
                    result = upsert_court_info(cursor, location_code, court_data)
                    if result[6] != "No change":
                        updated_courts.append(result)
                    else:
                        not_updated_courts.append(result)
                except Exception as e:
                    logger.error(f"Error processing court '{court_data['name']}': {e}")

            destination_db.connection.commit()

        generate_csv_report(updated_courts, not_updated_courts)

    except Exception as e:
        logger.error(f"Database operation failed: {e}")

if __name__ == "__main__":
    main()
