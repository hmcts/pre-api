
from .logging_utils import configure_logging
from .sql_queries import logger_queries


class FailedImportsLogger:
    def __init__(self, connection):
        self.connection = connection
        self.cursor = connection.cursor()
        self.log = configure_logging()

    def execute_query(self, query, values=None):
        try:
            if values:
                self.cursor.execute(query, values)
            else:
                self.cursor.execute(query)
            self.connection.commit()
        except Exception as e:
            self.log.error(
                f"Error executing query: {e}, Query: {query}, Values: {values}", exc_info=True)
            self.connection.rollback()

    def create_table(self, table_name):
        self._clear_table(table_name)
        self.execute_query(logger_queries['create_table_query'] % table_name)

    def _clear_table(self, table_name):
        check_query = f"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = %s)"
        self.execute_query(check_query, (table_name,))
        table_exists = self.cursor.fetchone()[0]

        if table_exists:
            delete_query = f"DELETE FROM {table_name}"
            self.execute_query(delete_query)

    def log_failed_imports(self, failed_imports):
        for failed_import in failed_imports:
            table_name = failed_import.get('table_name')
            table_id = failed_import.get('table_id')
            case_id = failed_import.get('case_id')
            recording_id = failed_import.get('recording_id')
            details = failed_import.get('details', 'None')

            values = (table_name, table_id, case_id, recording_id, details)
            self.execute_query(logger_queries['insert_query'], values)
