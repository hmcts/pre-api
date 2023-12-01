from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation

class ParticipantManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
<<<<<<< HEAD
=======
        self.failed_imports = set()
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)

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
<<<<<<< HEAD
                    participant_type = p_type.lower()
=======
                    participant_type = p_type.upper()
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)
                    first_name = participant[6]
                    last_name = participant[7]
                    created_at = parse_to_timestamp(participant[9])
                    modified_at = parse_to_timestamp(participant[11])

<<<<<<< HEAD
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

=======
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
                else:
                    self.failed_imports.add(('participants', id))
            else:
                self.failed_imports.add(('participants', id))

    def log_failed_imports(self, filename='failed_imports_log.txt'):
        with open(filename, 'w') as file:
            for table_name, failed_id in self.failed_imports:
                file.write(f"Table: {table_name}, ID: {failed_id}\n")
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)
