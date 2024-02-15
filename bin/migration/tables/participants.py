from .helpers import check_existing_record, parse_to_timestamp, audit_entry_creation, get_user_id

class ParticipantManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

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
                self.failed_imports.append({
                        'table_name': 'contacts',
                        'table_id': id,
                        'case_id': case_id,
                        'details': f'No Case ID associated with participant: {id}'
                    })
                continue

            if p_type is None:
                self.failed_imports.append({
                        'table_name': 'contacts',
                        'table_id': id,
                        'case_id': case_id,
                        'details': f'No participant type detail associated with participant: {id}'
                    })
                continue
            
            
            destination_cursor.execute(
                "SELECT id FROM public.cases WHERE id = %s", (case_id,)
            )
            case_id_exists = destination_cursor.fetchone()

            if case_id_exists is None:
                self.failed_imports.append({
                    'table_name': 'contacts',
                    'table_id': id,
                    'case_id': case_id,
                    'details': f'Invalid Case ID: {case_id} associated with participant: {id}'
                })
                continue
        
            if not check_existing_record(destination_cursor,'participants','case_id', case_id):
                participant_type = p_type.upper()

                if participant_type not in ('WITNESS', 'DEFENDANT'):
                    self.failed_imports.append({
                        'table_name': 'contacts',
                        'table_id': id,
                        'case_id': case_id,
                        'details': f'Invalid participant type: {p_type}.'
                    })
                    continue
                
                
                first_name = participant[6].strip() if participant[6] is not None else None 
                last_name = participant[7].strip() if participant[7] is not None else None
                
                active = True if participant[2] == '1' else False
                deleted_at = None
                if not active:
                    deleted_at = parse_to_timestamp(participant[11]) if participant[11] else parse_to_timestamp(participant[9])

                if first_name is None or last_name is None:
                    self.failed_imports.append({
                        'table_name': 'contacts',
                        'table_id': id,
                        'case_id': case_id,
                        'details': f'Participant is missing first / last name - first name: {first_name} last name: {last_name}'
                    })
                    continue

                
                created_at = parse_to_timestamp(participant[9])
                modified_at = parse_to_timestamp(participant[11])
                created_by = get_user_id(destination_cursor,participant[8])

                batch_participant_data.append((id, case_id, participant_type, first_name, last_name, deleted_at,created_at, modified_at, created_by))
                
        try:
            if batch_participant_data:
                
                destination_cursor.executemany(
                    """
                    INSERT INTO public.participants 
                        (id, case_id, participant_type, first_name, last_name, deleted_at,created_at, modified_at)
                    VALUES (%s, %s, %s, %s,%s, %s, %s, %s)
                    """,
                    [entry[:-1] for entry in batch_participant_data],
                )

                destination_cursor.connection.commit()

                for entry in batch_participant_data:
                    audit_entry_creation(
                        destination_cursor,
                        table_name="participants",
                        record_id=entry[0],
                        record=entry[1],
                        created_at=entry[5],
                        created_by=entry[8] if entry[8] is not None else None                    
                    )

        except Exception as e:
            self.failed_imports.append({'table_name': 'contacts','table_id': id,'details': str(e)})
        
        self.logger.log_failed_imports(self.failed_imports)
      