import os
import time

from db_utils import DatabaseManager

from tables.rooms import RoomManager
from tables.users import UserManager
from tables.roles import RoleManager
from tables.courts import CourtManager
from tables.courtrooms import CourtRoomManager
from tables.regions import RegionManager
from tables.courtregions import CourtRegionManager
from tables.portalaccess import PortalAccessManager
from tables.appaccess import AppAccessManager
from tables.cases import CaseManager
from tables.bookings import BookingManager
from tables.participants import ParticipantManager
from tables.bookingparticipants import BookingParticipantManager
from tables.capturesessions import CaptureSessionManager
from tables.recordings import RecordingManager
from tables.sharebookings import ShareBookingsManager
from tables.audits import AuditLogManager

from migration_reports.migration_tracker import MigrationTracker
from migration_reports.failed_imports_logger import FailedImportsLogger


# get passwords from env variables
source_db_name = os.environ.get('SOURCE_DB_NAME')
source_db_user = os.environ.get('SOURCE_DB_USER')
source_db_password = os.environ.get('SOURCE_DB_PASSWORD')
source_db_host = os.environ.get('SOURCE_DB_HOST')

source_db = DatabaseManager(
    database=source_db_name,
    user=source_db_user,
    password=source_db_password,
    host=source_db_host,
    port="5432",
)

destination_db_name = os.environ.get('DESTINATION_DB_NAME')
destination_db_user = os.environ.get('DESTINATION_DB_USER')
destination_db_password = os.environ.get('DESTINATION_DB_PASSWORD')
destination_db_host = os.environ.get('DESTINATION_DB_HOST')

destination_db = DatabaseManager(
    database=destination_db_name,
    user=destination_db_user,
    password=destination_db_password,
    host=destination_db_host,
    port="5432",
)


logger = FailedImportsLogger()
logger.load_existing_entries('migration_reports/failed_imports_log.txt')


# managers for different tables
room_manager = RoomManager(source_db.connection.cursor(), logger)
user_manager = UserManager(source_db.connection.cursor(), logger)
role_manager = RoleManager(source_db.connection.cursor(), logger)
court_manager = CourtManager(source_db.connection.cursor(), logger)
courtroom_manager = CourtRoomManager(logger)
region_manager = RegionManager(logger)
court_region_manager = CourtRegionManager(logger)
portal_access_manager = PortalAccessManager(source_db.connection.cursor(), logger)
app_access_manager = AppAccessManager(source_db.connection.cursor(), logger)
case_manager = CaseManager(source_db.connection.cursor(), logger)
booking_manager = BookingManager(source_db.connection.cursor(), logger)
participant_manager = ParticipantManager(source_db.connection.cursor(), logger)
booking_participant_manager = BookingParticipantManager(source_db.connection.cursor(), logger)
capture_session_manager = CaptureSessionManager(source_db.connection.cursor(), logger)
recording_manager = RecordingManager(source_db.connection.cursor(), logger)
share_bookings_manager = ShareBookingsManager(source_db.connection.cursor(), logger)
audit_log_manager = AuditLogManager(source_db.connection.cursor(), logger)

total_migration_time = 0 

def clear_total_migration_time():
    global total_migration_time
    total_migration_time = 0

def migrate_manager_data(manager, destination_cursor):
    global total_migration_time 
    start_time = time.time()
    print(f"Migrating data for {manager.__class__.__name__}...")

    if hasattr(manager, 'get_data') and callable(getattr(manager, 'get_data')):
        source_data = manager.get_data()
        manager.migrate_data(destination_cursor, source_data)
    else:
        manager.migrate_data(destination_cursor)

    end_time = time.time()
    time_taken = end_time - start_time
    total_migration_time += time_taken
    print(f"Data migration for {manager.__class__.__name__} complete in : {time_taken:.2f} seconds.\n")

def main():
    destination_db_cursor = destination_db.connection.cursor()

    migrate_manager_data(room_manager, destination_db_cursor)
    migrate_manager_data(user_manager, destination_db_cursor) 
    migrate_manager_data(role_manager, destination_db_cursor) 
    migrate_manager_data(court_manager, destination_db_cursor) 
    migrate_manager_data(courtroom_manager, destination_db_cursor) 
    migrate_manager_data(region_manager, destination_db_cursor) 
    migrate_manager_data(court_region_manager, destination_db_cursor) 
    migrate_manager_data(portal_access_manager, destination_db_cursor)
    migrate_manager_data(app_access_manager, destination_db_cursor) 
    migrate_manager_data(case_manager, destination_db_cursor) 
    migrate_manager_data(booking_manager, destination_db_cursor)
    migrate_manager_data(participant_manager, destination_db_cursor)
    migrate_manager_data(capture_session_manager, destination_db_cursor)
    migrate_manager_data(recording_manager, destination_db_cursor)
    migrate_manager_data(booking_participant_manager, destination_db_cursor)
    migrate_manager_data(share_bookings_manager, destination_db_cursor)
    migrate_manager_data(audit_log_manager, destination_db_cursor)

    counter = MigrationTracker(source_db.connection, destination_db.connection)
    counter.print_summary() # prints a table summary 
    counter.log_records_count(total_migration_time) # logs a record every time the script is run 

    clear_total_migration_time()
    source_db.close_connection()
    destination_db.close_connection()

if __name__ == "__main__":
    main()
