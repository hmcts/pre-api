from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports

class ParticipantManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.contacts")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        for participant in source_data:
            id = participant[0]
            
            destination_cursor.execute(
                "SELECT case_id FROM public.bookings WHERE id = %s", (participant[4],)
            )
            case_id = destination_cursor.fetchone()
            p_type = participant[3]

            if case_id:
                if not check_existing_record(destination_cursor,'participants','case_id', case_id) and p_type:
                    participant_type = p_type.upper()
                    first_name = participant[6]
                    last_name = participant[7]
                    created_at = parse_to_timestamp(participant[9])
                    modified_at = parse_to_timestamp(participant[11])

                    try:
                        destination_cursor.execute(
                            """
                            INSERT INTO public.participants 
                                (id, case_id, participant_type, first_name, last_name, created_at, modified_at)
                            VALUES (%s, %s, %s, %s, %s, %s, %s )
                            """,
                            (id, case_id, participant_type, first_name, last_name,  created_at, modified_at),
                        )

                        created_by = participant[8]
                        audit_entry_creation(
                            destination_cursor,
                            table_name="participants",
                            record_id=id,
                            record=case_id,
                            created_at=created_at,
                            created_by=created_by,
                        )

                    except Exception as e:  
                        self.failed_imports.add(('participants', id))
                        log_failed_imports(self.failed_imports)
                        
                else:
                    self.failed_imports.add(('participants', id))
                    log_failed_imports(self.failed_imports)
            else:
                    self.failed_imports.add(('participants', id))
                    log_failed_imports(self.failed_imports)
