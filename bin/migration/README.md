# Database Migration Script
This script manages the migration of data from a source database to a destination database.

## How to Run the Script
1. **Set Environment Variables:** :
    ```
    export SOURCE_DB_NAME=<source_db_name>
    export SOURCE_DB_USER=<source_db_user>
    export SOURCE_DB_PASSWORD=<source_db_password>
    export SOURCE_DB_HOST=<source_db_host>
    export DESTINATION_DB_NAME=<destination_db_name>
    export DESTINATION_DB_USER=<destination_db_user>
    export DESTINATION_DB_PASSWORD=<destination_db_password>
    export DESTINATION_DB_HOST=<destination_db_host>   
    ```
2. **Install Dependencies:** Install the required Python packages if not installed already:
    ```
    pip install -r requirements.txt
    ```
3. **Execute the Script:** Run the migration script:
    ```
    python main_script.py
    ```

### DatabaseManager Class
##### Methods:
- **`__init__(self, database, user, password, host, port)`:**  Initialises the DatabaseManager class and establishes connections to databases.
- **`execute_query(self, query, params=None)`:** Executes queries and fetches results.
- **`close_connection(self)`:** Closes the database connections.


### MigrationTracker Class
This is responsible for tracking and reporting on the progress of the data migration.
##### Methods:
- **`__init__(self, source_conn, destination_conn, file_path, migration_summary_log)`:**  Initialises the MigrationTracker class with source and destination database connections, along with file path for logging failed imports and migration summary logs.
- **`fetch_source_data(self)`:** Executes SQL queries on the source database to retrieve data.
- **`count_records_in_source_tables(self)`:** Counts the number of records in each source table specified in the source_table_queries dictionary.
- **`count_records_in_destination_tables(self)`:** Counts the number of records in each destination table by querying the destination database directly.
- **`count_failed_imports(self)`:**  Counts the number of failed imports by parsing the failed imports log file.
- **`print_summary(self)`:** Prints a summary table showing the number of records in source tables, destination tables, and failed imports for each table.
**`log_records_count(self, total_migration_time)`:** Logs the total count of records in the destination database, the count of failed imports, the date and time of the script execution, and the total migration time in the migration summary log file.

### FailedImportsLogger Class
This is responsible for logging failed imports during the data migration process. 
##### Methods:
- **`__init__(self)`:**  Initialises the FailedImportsLogger object with an empty set to store existing failed import entries.
- **`clear_migrations_file(filename='failed_imports_log.txt')`:** Clears the failed imports log before the migration to avoid duplicate entries
- **`load_existing_entries(self, filename)`:** Reads the existing failed import entries from the failed_imports_log.txt and populates the existing_entries_cache set.
- **`log_failed_imports`:** Logs new failed import entries to the failed_imports_log.txt, appending them to existing entries if any.

### Helper Functions
##### Methods:
- **`parse_to_timestamp(input_text)`:** Parses date strings into UK timestamps, handling various date formats and returning the current time in the UK timezone if the input is invalid or empty.
- **`check_existing_record(db_connection, table_name, field, record)`:** Checks if a record exists in the database.
- **`audit_entry_creation(db_connection, table_name, record_id, record, created_at=None, created_by="Data Entry")`:** Creates an audit entry in the database for a new record.
- **`log_failed_imports(failed_imports, filename='failed_imports_log.txt')`:** Writes to failed_imports_log if record import fails
- **`get_user_id(db_connection, email)`:** Gets the user ID associated with an email from the users table.

### Main Logic 
1. Initialises database connections.
2. Executes migration logic for each table manager.
3. During this process, it tracks any failed imports and logs them to the failed_imports_log.txt file using the FailedImportsLogger class.
4. After completing the migration process and handling any failed imports, logs a summary of the migration process into the migration_summary_log.txt file using the MigrationTracker class.
5. Closes database connections.

### Table Managers
These managers handle the migration of data for various entities:
- RoomManager: Handles room data migration.
- UserManager: Handles user data migration.
- RoleManager: Handles user roles data migration.
- CourtManager: Handles courts data migration, including a default court.
- CourtRoomManager: Handles associations between courts and rooms.
- RegionManager: Handles regions data migration.
- CourtRegionManager: Handles associations between courts and regions.
- PortalAccessManager: Handles user access to portal.
- AppAccessManager: Handles user access to application.
- CaseManager: Handles cases data migration.
- BookingManager: Handles booking data migration.
- ParticipantManager: Handles contacts data migration.
- BookingParticipantManager: Handles associations between bookings and participants.
- CaptureSessionManager: Handles capture session data migration.
- RecordingManager: Handles recordings data migration.
- ShareBookingsManager: Handles videopermissions data migrations
- AuditLogManager: Handles audit table data migration
