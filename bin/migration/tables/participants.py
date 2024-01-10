from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, log_failed_imports

class ParticipantManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()

    def get_data(self):
        self.source_cursor.execute("SELECT * FROM public.contacts")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_data):
        batch_participant_data = []

        created_by = None

        for participant in source_data:
            id = participant[0]
            p_type = participant[3]
            case_id = participant[4]

            if case_id is None:
                self.failed_imports.add(('contacts', id, 'No case id associated with this participant'))
                continue

            if p_type is None:
                self.failed_imports.add(('contacts', id, 'No participant type detail'))
                continue
            
            
            destination_cursor.execute(
                "SELECT id FROM public.cases WHERE id = %s", (case_id,)
            )
            case_id_exists = destination_cursor.fetchone()

            if case_id_exists is None:
                self.failed_imports.add(('contacts', id, f'Invalid case id {case_id} associated with this participant'))
                continue
        
            if not check_existing_record(destination_cursor,'participants','case_id', case_id):
                participant_type = p_type.upper()

                if participant_type not in ('WITNESS', 'DEFENDANT'):
                    self.failed_imports.add(('contacts', id, f'Invalid participant type: {p_type}'))
                    continue
                
                first_name = participant[6]
                last_name = participant[7]
                if (first_name is None) or (last_name is None):
                    self.failed_imports.add(('contacts', id, 'no participant names'))
                    continue
                
                created_at = parse_to_timestamp(participant[9])
                modified_at = parse_to_timestamp(participant[11])
                created_by = participant[8]

                batch_participant_data.append((id, case_id, participant_type, first_name, last_name, created_at, modified_at))
                
        try:
            if batch_participant_data:
                
                destination_cursor.executemany(
                    """
                    INSERT INTO public.participants 
                        (id, case_id, participant_type, first_name, last_name, created_at, modified_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """,
                    batch_participant_data,
                )

                destination_cursor.connection.commit()

                for entry in batch_participant_data:
                    audit_entry_creation(
                        destination_cursor,
                        table_name="participants",
                        record_id=entry[0],
                        record=entry[1],
                        created_at=entry[5],
                        created_by=created_by
                    )

        except Exception as e:
            self.failed_imports.add(('contacts', id, e))
        
        
        log_failed_imports(self.failed_imports) 
      