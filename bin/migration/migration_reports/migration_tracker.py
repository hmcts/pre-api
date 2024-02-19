from datetime import datetime
from migration_reports.sql_queries import source_table_queries
from migration_reports.constants import table_mapping

class MigrationTracker:
    def __init__(self, source_conn, destination_conn, file_path='migration_reports/failed_imports_log.txt', migration_summary_log='migration_reports/migration_summary_logs.txt'):
        self.source_conn = source_conn
        self.destination_conn = destination_conn
        self.file_path = file_path
        self.migration_summary_log = migration_summary_log
        self.total_migration_time = 0
       
    def fetch_source_data(self, query):
        cursor = self.source_conn.cursor()
        cursor.execute(query)
        data = cursor.fetchone()[0]
        cursor.close()
        return data

    def count_records_in_source_tables(self):
        table_counts = {}
        for source_table, query in source_table_queries.items():
            table_counts[source_table] = self.fetch_source_data(query)

        return table_counts
       
    def count_records_in_destination_tables(self):
        cursor = self.destination_conn.cursor()
        cursor.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name != 'temp_recordings'")
        tables = cursor.fetchall()

        table_counts = {}
        for table in tables:
            table_name = table[0]
            cursor.execute(f"SELECT COUNT(*) FROM public.{table_name}")
            count = cursor.fetchone()[0]
            table_counts[table_name] = count

        cursor.close()
        return table_counts

    def count_failed_imports(self):
        table_counts = {}
        with open(self.file_path, 'r') as file:
            for line in file:
                split_line = line.split('|')
                if len(split_line) > 1:
                    table_name = split_line[1].strip()
                
                if table_name in table_counts:
                    table_counts[table_name] += 1
                else:
                    table_counts[table_name] = 1
        return table_counts

    def print_summary(self):
        
        print(f"| {'Table Name'.ljust(20)} | {'Source DB Records'.ljust(18)} | {'Destination DB Records'.ljust(26)} | {'Failed Imports Logs'.ljust(19)}  ")
        print(f"| {'------------'.ljust(20)} | {'------------------'.ljust(18)} | {'------------------'.ljust(26)} | {'---------------'.ljust(19)}  ")

        source_table_counts = self.count_records_in_source_tables()
        destination_table_counts = self.count_records_in_destination_tables()
        failed_import_counts = self.count_failed_imports() 

        for source_table, destination_table in table_mapping.items():
            source_records = source_table_counts.get(source_table, '-')
            destination_records = destination_table_counts.get(destination_table, '-')
            failed_import_count = failed_import_counts.get(source_table, '-')
        
            print(f"| {destination_table.ljust(20)} | {str(source_records).ljust(18)} | {str(destination_records).ljust(26)} | {str(failed_import_count).ljust(19)}")

    def log_records_count(self, total_migration_time):
        
        self.total_migration_time = total_migration_time
        destination_counts = self.count_records_in_destination_tables()
        failed_imports_counts = self.count_failed_imports() 

        total_destination_db_count = sum(destination_counts.values())
        total_failed_records = sum(failed_imports_counts.values()) - 2 # first two lines in txt file are for header
        date_and_time = datetime.now()

        with open(self.migration_summary_log, 'a+') as file:
            file.seek(0, 2)  
            if file.tell() == 0:
                file.write(f"| {'Destination DB count'.ljust(20)} | {'Failed migration log count'.ljust(26)} | {'Date/Time script run'.ljust(28)} | {'Total migration time'.ljust(24)} |\n")
                file.write(f"| {'--------------------'.ljust(20)} | {'------------------------'.ljust(26)} | {'---------------------'.ljust(28)} | {'--------------------'.ljust(24)} |\n")

            file.write(f"| {str(total_destination_db_count).ljust(20)} | {str(total_failed_records).ljust(26)} | {str(date_and_time).ljust(28)} | {str(total_migration_time).ljust(24)} |\n")



