# Database Migration Script

This script manages the migration of data from a source database to a destination database.

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
Handles the migration of court-related data.

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



| Table Name         | Source DB Records | Destination DB Records |
|--------------------|-------------------|------------------------|
| recordings         | 643               | 585                    | 
| share_recordings   | 0                 | 0                      |
| portal_access      | 0                 | 0                      |
| audits             | 9490              | 0                      |
| courts             | 9                 | 10                     |
| court_region       | 0                 | 10                     |
| regions            | 0                 | 10                     |
| courtrooms         | 0                 | 20                     |
| rooms              | 20                | 20                     |
| participants       | 1746              | 465                    |
| bookings           | 0                 | 475                    |
| cases              | 484               | 475                    |
| booking_participant| 0                 | 475                    |
| roles              | 0                 | 5                      |
| role_permission    | 0                 | 0                      |
| permissions        | 0                 | 0                      |
| users              | 264               | 264                    |
| app_access         | 0                 | 153                    |
| capture_sessions   | 0                 | 573                    |
