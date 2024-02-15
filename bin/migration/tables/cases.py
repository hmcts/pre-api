from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, get_user_id

class CaseManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.cases")
        return self.source_cursor.fetchall()

    def get_case_deleted_date(self, case_id, modified_at_date):
        self.source_cursor.execute("""
            SELECT createdon FROM audits 
            WHERE auditdetails = 'Case marked as Deleted.' AND caseuid = %s
        """, (case_id,))

        deleted_date = self.source_cursor.fetchall()
        if deleted_date:
            deleted_date_str =deleted_date[0][0]
            return parse_to_timestamp(deleted_date_str)
        else:
            return modified_at_date

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
                self.failed_imports.append({
                    'table_name': 'cases',
                    'table_id': id,
                    'case_id': id,
                    'details': 'Null value for case reference.'
                })
                continue

            if not check_existing_record(destination_cursor,'cases', 'id', id):
                court_id = court_name_to_id.get(case[2])
                if court_id is None:
                    court_id = default_court_id

                test = False  
                created_at = parse_to_timestamp(case[5])
                created_by = get_user_id(destination_cursor,case[4])
                modified_at = parse_to_timestamp(case[7]) if case[7] is not None else created_at
                deleted_at = self.get_case_deleted_date(id, modified_at) if case[3] == "Deleted" else None


                cases_data.append((id, court_id, reference, test, deleted_at, created_at, modified_at))
                
                audit_entry_creation(
                    destination_cursor,
                    table_name="cases",
                    record_id=id,
                    record=reference,
                    created_at=created_at,
                    created_by=created_by if created_by is not None else None
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
            self.failed_imports.append({'table_name': 'cases','table_id': id,'case_id': id, 'details': str(e)})
        
        self.logger.log_failed_imports(self.failed_imports)   
