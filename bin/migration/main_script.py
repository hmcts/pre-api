import os

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

import time

start_time = time.time()

# get passwords from env variables
source_db_password = os.environ.get('SOURCE_DB_PASSWORD')
destination_db_password = os.environ.get('DESTINATION_DB_PASSWORD')

# database connections
source_db = DatabaseManager(
    database="pre-pdb-demo",
    user="psqladmin",
    password=source_db_password,
    host="pre-db-demo.postgres.database.azure.com",
    port="5432",
)

destination_db = DatabaseManager(
    database="dev-pre-copy",
    user="psqladmin",
    password=destination_db_password,
    host="pre-db-dev.postgres.database.azure.com",
    port="5432",
)

# managers for different tables
room_manager = RoomManager(source_db.connection.cursor())
user_manager = UserManager(source_db.connection.cursor())
role_manager = RoleManager()
court_manager = CourtManager(source_db.connection.cursor())
courtroom_manager = CourtRoomManager()
region_manager = RegionManager()
court_region_manager = CourtRegionManager()
portal_access_manager = PortalAccessManager(source_db.connection.cursor())
app_access_manager = AppAccessManager(source_db.connection.cursor())
case_manager = CaseManager(source_db.connection.cursor())
booking_manager = BookingManager(source_db.connection.cursor())
participant_manager = ParticipantManager(source_db.connection.cursor())
booking_participant_manager = BookingParticipantManager()
capture_session_manager = CaptureSessionManager(source_db.connection.cursor())
recording_manager = RecordingManager(source_db.connection.cursor())

def migrate_manager_data(manager, destination_cursor):
    if hasattr(manager, 'get_data') and callable(getattr(manager, 'get_data')):
        source_data = manager.get_data()
        manager.migrate_data(destination_cursor, source_data)
    else:
        manager.migrate_data(destination_cursor)


def main():
    destination_db_cursor = destination_db.connection.cursor()

    migrate_manager_data(room_manager, destination_db_cursor) # 20 / 20 migrated
    migrate_manager_data(user_manager, destination_db_cursor) # 263 / 263 migrated
    migrate_manager_data(role_manager, destination_db_cursor) # Levels 1 - 4 & superuser
    migrate_manager_data(court_manager, destination_db_cursor) # 9 / 9 migrated and an added 'default court'
    migrate_manager_data(courtroom_manager, destination_db_cursor) # 20 PRE rooms
    migrate_manager_data(region_manager, destination_db_cursor) # 10 regions - not in current setup
    migrate_manager_data(court_region_manager, destination_db_cursor) # 10 court region associations - not in current setup
    migrate_manager_data(portal_access_manager, destination_db_cursor) # 52 users with Level 3 access migrated (58 users with no role set - not migrated)
    migrate_manager_data(app_access_manager, destination_db_cursor) # 153 users migrated (58 users with no role set - not migrated)
    migrate_manager_data(case_manager, destination_db_cursor) # 475 / 484 cases migrated (9 cases not migrated do not have a caseref)
    migrate_manager_data(booking_manager, destination_db_cursor) # 475 / 484 cases migrated (9 cases not migrated do not have a caseref)
    migrate_manager_data(participant_manager, destination_db_cursor) # 465 / 1747 partipants migrated - failed import ids in log
    migrate_manager_data(booking_participant_manager, destination_db_cursor)
    migrate_manager_data(capture_session_manager, destination_db_cursor)
    migrate_manager_data(recording_manager, destination_db_cursor)


    source_db.close_connection()
    destination_db.close_connection()

    end_time = time.time()
    execution_time = end_time - start_time
    print(f"Execution time: {execution_time} seconds")

if __name__ == "__main__":
    main()
