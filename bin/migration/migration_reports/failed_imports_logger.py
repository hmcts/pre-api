

class FailedImportsLogger:
    def __init__(self):
        self.existing_entries_cache = []

    def load_existing_entries(self, filename):
        try:
            with open(filename, 'r') as file:
                for line in file:
                    failed_import = [item.strip()for item in line.split('|') if item.strip()]

                    if len(failed_import) >= 4:
                        entry = {
                            'table_name': failed_import[0].strip(),
                            'table_id': failed_import[1].strip(),
                            'case_id': failed_import[2].strip(),
                            'recording_id': failed_import[3].strip(),
                            'details': failed_import[4]
                        }
                        self.existing_entries_cache.append(entry)
        except FileNotFoundError:
            pass

    def is_duplicate_entry(self, entry):
        for existing_entry in self.existing_entries_cache:
            if existing_entry['table_name'] == entry['table_name'] and \
            existing_entry['recording_id'] == entry['recording_id'] and \
            existing_entry['case_id'] == entry['case_id']:
                if existing_entry['details'].lower() == entry['details'].lower():
                    return True
        return False

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
                table_id = failed_import.get('table_id')
                case_id = failed_import.get('case_id', 'N/A')
                recording_id = failed_import.get('recording_id', 'N/A')
                details = failed_import.get('details')

                if not self.is_duplicate_entry({
                    'table_name': table_name,
                    # 'table_id': table_id,
                    'case_id': case_id,
                    'recording_id': recording_id,
                    'details': details
                }):
                    entry_str = f"| {str(table_name).ljust(22)} | {str(table_id).ljust(36)} | {str(case_id).ljust(36)} | {str(recording_id).ljust(36)} | {str(details)} \n"
                    file.write(entry_str)

                    self.existing_entries_cache.append({
                        'table_name': table_name,
                        'table_id': table_id,
                        'case_id': case_id,
                        'recording_id': recording_id,
                        'details': details
                    })
    

