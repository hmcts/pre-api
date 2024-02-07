class FailedImportsLogger:
    def __init__(self):
        self.existing_entries_cache = set()
    
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

    def log_failed_imports(self, failed_imports, filename='failed_imports_log.txt'):
        self.load_existing_entries(filename)

        with open(filename, 'a') as file:
            for entry in failed_imports:
                if len(entry) == 2:
                    table_name, failed_id = entry
                    details = 'Import failed'
                elif len(entry) == 3:
                    table_name, failed_id, details = entry
                else:
                    raise ValueError("Each entry in failed_imports should have 2 or 3 elements")
                
                if (table_name, failed_id) not in self.existing_entries_cache:
                    self.existing_entries_cache.add((table_name.strip(), failed_id.strip())) 
                    file.write(f"Table: {table_name}, ID: {failed_id}, Details: {details}\n")
