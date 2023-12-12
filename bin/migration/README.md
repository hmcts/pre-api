# Database Migration Script

This script manages the migration of data from a source database to a destination database.


## How to Run the Script

1. **Set Environment Variables:** :
    ```
    export SOURCE_DB_PASSWORD=<source_db_password>
    export DESTINATION_DB_PASSWORD=<destination_db_password>
    ```

2. **Install Dependencies:** Install the required Python packages if not installed already:
    ```
    pip install -r requirements.txt
    ```

3. **Execute the Script:** Run the migration script:
    ```
    python migration_script.py
    ```

4. **Test the migrated counts:** Run the summary script:
    ```
    python summary.py
    ```

## Summary
The `summary.py` file provides an overview of database record counts for the source and destination dbs and count of failed imports

## DatabaseManager Class

### Methods:
- **`__init__(self, database, user, password, host, port)`:** Initializes the DatabaseManager class and establishes connections to databases.
- **`execute_query(self, query, params=None)`:** Executes queries and fetches results.
- **`close_connection(self)`:** Closes the database connections.

## Helper Functions

### `parse_to_timestamp(input_text)`
Parses date strings into UK timestamps, handling various date formats and returning the current time in the UK timezone if the input is invalid or empty.

### `check_existing_record(db_connection, table_name, field, record)`
Checks if a record exists in the database.

### `audit_entry_creation(db_connection, table_name, record_id, record, created_at=None, created_by="Data Entry")`
Creates an audit entry in the database for a new record.

### `log_failed_imports(failed_imports, filename='failed_imports_log.txt')`
Writes to failed_imports_log if record import fails


## Main Logic 

1. Initializes database connections.
2. Executes migration logic for each table manager.
3. Closes database connections.

## Table Managers

### RoomManager
Handles the migration of room data.

### UserManager
Manages the migration of user data.

### RoleManager
Manages user roles migration.

### CourtManager
Handles the migration of court-related data. An added 'Default Court' added for records with no data of which courts they're tried in. 

### CourtRoomManager
Manages the migration of courtroom data.

### RegionManager
Manages the migration of region-related data.

### CourtRegionManager
Handles associations between courts and regions.

### PortalAccessManager
Manages user access to portals. The assumption is that Level 3 users have access to the Portal

### AppAccessManager
Handles user access to applications. The assumption is that all Roles except for Level 3 users have this access.

### CaseManager
Manages the migration of case-related data.

### BookingManager
Handles the migration of booking-related data.

### ParticipantManager
Manages the migration of participant-related data.

### BookingParticipantManager
Handles associations between bookings and participants.

### CaptureSessionManager
Manages the migration of capture session data.

### RecordingManager
Handles the migration of recording data.

