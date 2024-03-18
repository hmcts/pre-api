## Get recording durations from AMS and update database

This script fetches recording durations from Azure Blob Storage and updates the database accordingly. It performs three main steps:

1. **Fetch recordings from table and save to CSV**: Connects to the database, fetches recording IDs, and saves them to a CSV file.
2. **Update durations in DataFrame**: Updates the CSV file with recording durations fetched from Azure Blob Storage.
3. **Update recordings table in database**: Reconnects to the database and updates the recordings table with the durations.

### Setup
1. Ensure you have the required environment variables set for database connection:
- `DESTINATION_DB_NAME`: Name of the destination database
- `DESTINATION_DB_USER`: Username for database authentication
- `DESTINATION_DB_PASSWORD`: Password for database authentication
- `DESTINATION_DB_HOST`: Hostname or IP address of the database server

2. Ensure you have the required environment variables set for Azure Blob Storage connection:
- `AZURE_STORAGE_CONNECTION_STRING`: Connection string for Azure Blob Storage

### How to Run
Run the script with the appropriate step flag to execute the desired step:
**Note - Important:**  Ensure that the VPN is open for *only* Steps 1 and 3, and closed for Step 2.

- To perform Step 1 (Fetch recordings from table and save to CSV):
`python main.py --step 1`

- To perform Step 2 (Update durations in DataFrame):
`python main.py --step 2`

- To perform Step 3 (Update recordings table in database):
`python main.py --step 3`


