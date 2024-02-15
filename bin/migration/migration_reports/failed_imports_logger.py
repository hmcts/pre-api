class FailedImportsLogger:
    def __init__(self):
        self.existing_entries_cache = set()

    @staticmethod
    def clear_migrations_file(filename='migration_reports/failed_imports_log.txt'):
        with open(filename, 'w') as file:
            file.write("")
    
    def load_existing_entries(self, filename):
        try:
            with open(filename, 'r') as file:
                for line in file:
                    failed_import = line.strip().split(', ')
                    if len(failed_import) == 3:
                        table_name, failed_id, _ = failed_import  
                        self.existing_entries_cache.add((table_name, failed_id))
        except FileNotFoundError:
            pass

    def log_failed_imports(self, failed_imports, filename='migration_reports/failed_imports_log.txt'):
        # self.load_existing_entries(filename)

        with open(filename, 'a+') as file:
            file.seek(0, 2)       
            if file.tell() == 0:
                file.write(f"| {'Table Name'.ljust(22)} | {'ID'.ljust(36)} | {'Case ID'.ljust(36)} | {'Recording ID'.ljust(36)} | {'Details'.ljust(50)} \n")
                file.write(f"| {'-------------'.ljust(22)} | {'------------------------------------'.ljust(36)} | {'------------------------------------'.ljust(10)} | {'------------------------------------'.ljust(36)} | {'--------------------------------------------------'} \n")

            for failed_import in failed_imports:
                table_name = failed_import.get('table_name')
                table_id = failed_import.get('table_id')
                case_id = failed_import.get('case_id', 'N/A')
                recording_id = failed_import.get('recording_id', 'N/A')
                details = failed_import.get('details')

                if details is not None:
                    file.write(f"| {str(table_name).ljust(22)} | {str(table_id).ljust(36)} | {str(case_id).ljust(36)} | {str(recording_id).ljust(36)} | {str(details)} \n")

                
