import psycopg2
import os

# Connection
source_db_password = os.environ.get('SOURCE_DB_PASSWORD')
destination_db_password = os.environ.get('DESTINATION_DB_PASSWORD')

destination_conn = psycopg2.connect(
    database="dev-pre-copy",
    user="psqladmin",
    password=destination_db_password,
    host="pre-db-dev.postgres.database.azure.com",
    port="5432",
)

source_conn = psycopg2.connect(
    database="pre-pdb-demo",
    user="psqladmin",
    password=source_db_password,
    host="pre-db-demo.postgres.database.azure.com",
    port="5432",
)

# table mapping from old db table names to new
table_mapping = {
    'recordings': 'recordings',
    'share_recordings' : 'share_recordings',
    'portal_access' : 'portal_access',
    'audits': 'audits',
    'courts': 'courts',
    'court_region':'court_region',
    'regions':'regions',
    'courtrooms':'courtrooms',
    'rooms':'rooms',
    'contacts': 'participants',
    'bookings':'bookings',
    'cases': 'cases',
    'booking_participant':'booking_participant',
    'roles':'roles',
    'role_permission':'role_permission',
    'permissions': 'permissions',
    'users': 'users',
    'app_access':'app_access',
    'capture_sessions':'capture_sessions'
}

# Counts the number of records in all tables in a provided db connection
def count_records_in_all_tables(connection):
    cursor = connection.cursor()

    cursor.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")
    tables = cursor.fetchall()

    table_counts = {}
    for table in tables:
        table_name = table[0]
        cursor.execute(f"SELECT COUNT(*) FROM public.{table_name}")
        count = cursor.fetchone()[0]
        table_counts[table_name] = count

    cursor.close()
    return table_counts

# Parses the failed imports log file to count the number of failed imports for each tables.
def count_failed_imports(file_path):
    table_counts = {}
    
    with open(file_path, 'r') as file:
        for line in file:
            table_name = line.split(', ')[0].split(': ')[1].strip()
            
            if table_name in table_counts:
                table_counts[table_name] += 1
            else:
                table_counts[table_name] = 1
    
    return table_counts

source_table_counts = count_records_in_all_tables(source_conn)
destination_table_counts = count_records_in_all_tables(destination_conn)

file_path = 'failed_imports_log.txt'
failed_imports = count_failed_imports(file_path)

# Displays the record counts in both source and destination db and the failed logs. This is to monitor for data loss.
def print_summary(source_counts, destination_counts, failed_imports):
    print(f"| {'Table Name'.ljust(20)} | {'Source DB Records'.ljust(18)} | {'Destination DB Records'.ljust(26)} | {'Failed Imports Logs'.ljust(19)}  ")
    print(f"| {'------------'.ljust(20)} | {'------------------'.ljust(18)} | {'------------------'.ljust(26)} | {'---------------'.ljust(19)}  ")

    for source_table, destination_table in table_mapping.items():
        source_records = source_counts.get(source_table, '-')
        destination_records = destination_counts.get(destination_table, '-')
        failed_import_count = failed_imports.get(source_table, '-')
       
        print(f"| {destination_table.ljust(20)} | {str(source_records).ljust(18)} | {str(destination_records).ljust(26)} | {str(failed_import_count).ljust(19)}")
    
print_summary(source_table_counts, destination_table_counts, failed_imports)

source_conn.close()
destination_conn.close()



