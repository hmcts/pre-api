import psycopg2

def count_records_in_all_tables_source_db(connection):
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


conn = psycopg2.connect(
    database="pre-pdb-demo",
    user="psqladmin",
    password="***",
    host="pre-db-demo.postgres.database.azure.com",
    port="5432",
)

counts = count_records_in_all_tables_source_db(conn)
for table, count in counts.items():
    print(f"SOURCE DB - Table '{table}' has {count} records.")
print("")
conn.close()


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

# Example usage:
conn = psycopg2.connect(
    database="dev-pre-copy",
    user="psqladmin",
    password="***",
    host="pre-db-dev.postgres.database.azure.com",
    port="5432",
)

counts = count_records_in_all_tables_destination_db(conn)
for table, count in counts.items():
    print(f"DESTINATION DB - Table '{table}' has {count} records.")
print("")
conn.close()
