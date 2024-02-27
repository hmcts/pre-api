from .logging_utils import configure_logging
from migration_reports.sql_queries import source_table_queries
from migration_reports.constants import table_mapping
from .sql_queries import migration_tracker_queries


class MigrationTracker:
    def __init__(self, source_conn, destination_conn):
        self.source_cursor = source_conn.cursor()
        self.destination_cursor = destination_conn.cursor()
        self.total_migration_time = 0
        self.log = configure_logging()

    def _execute_query(self, cursor, query):
        try:
            cursor.execute(query)
        except Exception as e:
            self.log.error(
                f"Error executing query: {e}, Query: {query}", exc_info=True)

    def _count_records_in_source_tables(self):
        table_counts = {}
        for source_table, query in source_table_queries.items():
            self._execute_query(self.source_cursor, query)
            result = self.source_cursor.fetchone()
            if result is not None:
                table_counts[source_table] = result[0]

        # Get counts from join_tables and update table_counts
        join_table_counts = self._count_join_tables()
        for table, count in join_table_counts.items():
            if table not in table_counts:
                table_counts[table] = count
            else:
                table_counts[table] += count
        return table_counts

    def _count_join_tables(self):
        db_tables = ['court_region', 'courtrooms',
                     'booking_participant', 'regions']
        table_counts = {}
        for table in db_tables:
            count_query = f"SELECT COUNT(*) FROM public.{table}"
            self._execute_query(self.destination_cursor, count_query)
            result = self.destination_cursor.fetchone()
            if result:
                count = result[0]
                table_counts[table] = count
        return table_counts

    def _count_tables(self, query):
        self._execute_query(self.destination_cursor, query)
        result = self.destination_cursor.fetchone()
        if result:
            return result[0]

    def _count_failed_records(self):
        table_counts = {}
        query = "SELECT table_name, COUNT(*) FROM public.failed_imports GROUP BY table_name"
        self._execute_query(self.destination_cursor, query)
        rows = self.destination_cursor.fetchall()
        for row in rows:
            table_name, count = row
            table_counts[table_name] = count
        return table_counts

    def _count_records_in_destination_tables(self):
        db_tables = []
        query = "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name != 'failed_imports' AND table_name != 'temp_recordings'"
        self._execute_query(self.destination_cursor, query)
        db_tables = self.destination_cursor.fetchall()

        if db_tables is not None:
            table_counts = {}
            for table in db_tables:
                table_name = table[0]
                count_query = f"SELECT COUNT(*) FROM public.{table_name}"
                self._execute_query(self.destination_cursor, count_query)
                count = self.destination_cursor.fetchone()[0]
                table_counts[table_name] = count

            # Deduct migrated count from audit table
            deducted_count = self._count_tables(
                migration_tracker_queries['count_audit_records_query'])
            if deducted_count:
                table_counts['audits'] -= deducted_count
            return table_counts

    def log_records_count(self, runtime):
        source_counts = self._count_records_in_source_tables()
        destination_counts = self._count_records_in_destination_tables()
        failed_migration_counts = self._count_tables(
            migration_tracker_queries['count_failed_migrations_query'])

        count_source_tables = sum(
            count for table, count in source_counts.items())
        count_destination_tables = sum(
            count for table, count in destination_counts.items() if table != 'failed_imports')
        count_records_not_migrated = count_source_tables - \
            count_destination_tables - failed_migration_counts

        self.log.info(
            f"Source: {count_source_tables} - Destination: {count_destination_tables} - Failed: {failed_migration_counts} - Not migrated: {count_records_not_migrated} - Elapsed Time: {runtime} seconds")

    def print_summary(self):
        self._print_table_header()
        source_table_counts = self._count_records_in_source_tables()
        destination_table_counts = self._count_records_in_destination_tables()
        failed_record_counts = self._count_failed_records()

        for source_table, destination_table in table_mapping.items():
            source_records = source_table_counts.get(source_table, '-')
            destination_records = destination_table_counts.get(
                destination_table, '-')
            failed_records = failed_record_counts.get(destination_table, '-')
            self._print_table_row(
                destination_table, source_records, destination_records, failed_records)

    def _print_table_row(self, table_name, source_records, destination_records, failed_records):
        print(f"| {table_name.ljust(20)} | {str(source_records).ljust(18)} | {str(destination_records).ljust(26)} | {str(failed_records).ljust(18)}")

    def _print_table_header(self):
        print(f"| {'Table Name'.ljust(20)} | {'Source DB Records'.ljust(18)} | {'Destination DB Records'.ljust(26)} | {'Failed Imports Logs'.ljust(19)}  ")
        print(f"| {'------------'.ljust(20)} | {'------------------'.ljust(18)} | {'------------------'.ljust(26)} | {'---------------'.ljust(19)}  ")
