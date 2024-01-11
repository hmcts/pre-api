from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports

class CaseManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.cases")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        destination_cursor.execute("SELECT id, name FROM public.courts")
        courts_data = destination_cursor.fetchall()
        court_name_to_id = {court[1]: court[0] for court in courts_data}  

        default_court_name = "Default Court"  
        default_court_id = court_name_to_id.get(default_court_name) 

        cases_data = []
        for case in source_data:
            reference = case[1]
            id = case[0]

            if reference is None:
                self.failed_imports.add(('cases', id, 'Null value for reference'))
                continue

            if not check_existing_record(destination_cursor,'cases', 'id', id):
                court_id = court_name_to_id.get(case[2])
                if court_id is None:
                    court_id = default_court_id

                test = False  
                created_at = parse_to_timestamp(case[5])
                created_by = case[4]
                modified_at = parse_to_timestamp(case[7])
                # modified_at = parse_to_timestamp(case[7]) if case[7] else None
                deleted_at = parse_to_timestamp(case[7]) if case[3] == "Deleted" else None

                cases_data.append((id, court_id, reference, test, deleted_at, created_at, modified_at))
                
                audit_entry_creation(
                    destination_cursor,
                    table_name="cases",
                    record_id=id,
                    record=reference,
                    created_at=created_at,
                    created_by=created_by,
                )
            
        try: 
            if cases_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.cases
                        (id, court_id, reference, test,deleted_at, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s,%s, %s)
                    """,
                    cases_data,
                )
                destination_cursor.connection.commit()

        except Exception as e:  
            self.failed_imports.add(('cases', id,e))
        
        log_failed_imports(self.failed_imports)   
