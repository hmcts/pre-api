from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports
import uuid

class CaseManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.cases")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        # creating a temporary table for the unique case_ref values
        destination_cursor.execute(
            """CREATE TABLE IF NOT EXISTS public.temp_cases (
                                    booking_id UUID PRIMARY KEY,
                                    case_id UUID,
                                    reference VARCHAR(25),
                                    court_name VARCHAR(250),
                                    court_id UUID,
                                    created_at VARCHAR(50),
                                    created_by VARCHAR(100),
                                    modified_at VARCHAR(50)
                                    )
                                """
        )

        destination_cursor.execute("SELECT id FROM public.courts WHERE name = 'Default Court'")
        default_court_id = destination_cursor.fetchone()

        temp_cases_data = []
        for case in source_data:
            reference = case[1]
            
            if reference is None:
                self.failed_imports.add(('cases', case[0]))
                log_failed_imports(self.failed_imports)

            else:
                if not check_existing_record(destination_cursor,'temp_cases', 'reference', reference):
                    destination_cursor.execute(
                        "SELECT id FROM public.courts WHERE name = %s", (case[2],)
                    )
                    court_id = destination_cursor.fetchone()
                    booking_id= case[0]
                    case_id = str(uuid.uuid4())
                    court_name = case[2]
                    created_at = parse_to_timestamp(case[5])
                    created_by = case[4]
                    modified_at = parse_to_timestamp(case[6])
                    temp_cases_data.append((booking_id, case_id, reference, court_name, court_id, created_at, created_by, modified_at))

        destination_cursor.executemany(
            """INSERT INTO public.temp_cases (booking_id, case_id, reference, court_name, court_id, created_at, created_by, modified_at)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)""",
            temp_cases_data
        )   
        destination_cursor.connection.commit()

        destination_cursor.execute("SELECT * FROM public.temp_cases")
        source_temp_cases_data = destination_cursor.fetchall()

        cases_data = []
        for case in source_temp_cases_data:
            reference = case[2]

            if not check_existing_record(destination_cursor,'cases', 'reference', reference):
                id = case[1]
                court_id = default_court_id if case[4] is None else case[4]
                test = False  # to verity the default should be False
                created_at = parse_to_timestamp(case[5])
                modified_at = parse_to_timestamp(case[7])
                created_by = case[6]

                cases_data.append((id, court_id, reference, test, created_at, modified_at))
                
                audit_entry_creation(
                    destination_cursor,
                    table_name="cases",
                    record_id=id,
                    record=reference,
                    created_at=created_at,
                    created_by=created_by,
                )
            else:
                self.failed_imports.add(('cases', id))
                log_failed_imports(self.failed_imports)
        try: 
            if cases_data:
                destination_cursor.executemany(
                    """
                    INSERT INTO public.cases
                        (id, court_id, reference, test, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    cases_data,
                )
                destination_cursor.connection.commit()

        except Exception as e:  
            self.failed_imports.add(('cases', id))
            log_failed_imports(self.failed_imports)
