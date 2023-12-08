from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports
from datetime import datetime


class BookingManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.cases")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        for booking in source_data:
            id = booking[0]

            if not check_existing_record(destination_cursor,'bookings', 'id', id):
                destination_cursor.execute(
                    "SELECT * FROM public.temp_cases WHERE reference = %s", (booking[1],)
                )
                case_details = destination_cursor.fetchone()
                case_id = case_details[1] if case_details else None
                
                try:
                    if case_id:
                        # Check if case_id exists in the cases table
                        if check_existing_record(destination_cursor,'cases','id', case_id):
                            scheduled_for = (datetime.today()) 
                            created_at = parse_to_timestamp(case_details[1])
                            modified_at = parse_to_timestamp(case_details[3])
                            created_by = case_details[2]

                            destination_cursor.execute(
                                """
                                INSERT INTO public.bookings 
                                    (id, case_id, scheduled_for, created_at, modified_at)
                                VALUES (%s, %s, %s, %s, %s )
                                """,
                                (id, case_id, scheduled_for, created_at, modified_at),
                            )

                            audit_entry_creation(
                                destination_cursor,
                                table_name="bookings",
                                record_id=id,
                                record=case_id,
                                created_at=created_at,
                                created_by=created_by,
                            )
                except Exception as e:  
                    self.failed_imports.add(('bookings', id))
                    log_failed_imports(self.failed_imports)
            else:
                self.failed_imports.add(('bookings', id))
                log_failed_imports(self.failed_imports)
    
   