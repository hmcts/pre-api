class FailedImportsLogger:
    def __init__(self):
        self.existing_entries_cache = set()

    def load_existing_entries(self, filename):
        self.existing_entries_cache.clear()
        try:
            with open(filename, 'r') as file:
                for line in file:
                    failed_import = [item.strip()
                                     for item in line.split('|') if item.strip()]

                    if len(failed_import) >= 3:
                        table_name = failed_import[0].strip()
                        table_id = failed_import[0].strip()
                        case_id = failed_import[2].strip() if failed_import[2].strip() != None else 'N/A'
                        recording_id = failed_import[3].strip() if failed_import[3].strip() != None else 'N/A'
                        details = failed_import[4].strip()

                        entry = (table_name,table_id, case_id, recording_id, details)
                        self.existing_entries_cache.add(entry)
        except FileNotFoundError:
            pass

    def is_duplicate_entry(self, entry):
        return entry in self.existing_entries_cache

    
    def log_failed_imports(self, failed_imports, filename='migration_reports/failed_imports_log.txt'):
        self.existing_entries_cache.clear()
        self.load_existing_entries(filename)

        with open(filename, 'a+') as file:
            file.seek(0, 2)
            if file.tell() == 0:
                file.write(
                    f"| {'Table Name'.ljust(22)} | {'ID'.ljust(36)} | {'Case ID'.ljust(36)} | {'Recording ID'.ljust(36)} | {'Details'.ljust(50)} \n")
                file.write(f"| {'-------------'.ljust(22)} | {'------------------------------------'.ljust(36)} | {'------------------------------------'.ljust(10)} | {'------------------------------------'.ljust(36)} | {'--------------------------------------------------'} \n")

            for failed_import in failed_imports:
                table_name = failed_import.get('table_name')
                table_id = failed_import.get('table_id') or 'None'
                case_id = failed_import.get('case_id', 'N/A') or 'None'
                recording_id = failed_import.get(
                    'recording_id', 'N/A') or 'None'
                details = failed_import.get('details') or 'None'

                if not self.is_duplicate_entry((table_name, case_id, recording_id, details)):
                    failed_migration_str = f"| {str(table_name).ljust(22)} | {str(table_id).ljust(36)} | {str(case_id).ljust(36)} | {str(recording_id).ljust(36)} | {str(details)} \n"
                    file.write(failed_migration_str)
                    existing_entry_str = (
                        table_name, table_id, case_id, recording_id, details)
                    self.existing_entries_cache.add(existing_entry_str)

