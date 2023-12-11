import psycopg2
import os

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

def count_records_in_all_tables_source_db(connection):
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

counts = count_records_in_all_tables_source_db(source_conn)
for table, count in counts.items():
    print(f"SOURCE DB - Table '{table}' has {count} records.")
print("")
source_conn.close()


def count_records_in_all_tables_destination_db(connection):
    cursor = connection.cursor()

    # Get the list of all tables in the database
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

counts = count_records_in_all_tables_destination_db(destination_conn)
for table, count in counts.items():
    print(f"DESTINATION DB - Table '{table}' has {count} records.")
print("")
destination_conn.close()

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

file_path = 'failed_imports_log.txt'
result = count_failed_imports(file_path)
print("Failed record counts: ",result)  


