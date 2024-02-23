from .helpers import check_existing_record

class BookingParticipantManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute(
            "SELECT recordinguid, defendants, witnessnames, caseuid FROM recordings")
        return self.source_cursor.fetchall()
    
    def get_booking_id(self, connection, recording_id):
            connection.execute("""
                SELECT booking_id, scheduled_for
                FROM public.temp_recordings
                WHERE recording_id = %s 
            """, (recording_id,))
            result = connection.fetchone()
            return result

    def migrate_data(self, destination_cursor, source_data):
        destination_cursor.execute("SELECT id FROM public.participants")
        participant_ids = [row[0] for row in destination_cursor.fetchall()]

        for recording in source_data:
            recording_id = recording[0]
            defendants_list = recording[1].split(',') if recording[1] else []
            witnesses_list = recording[2].split(',') if recording[2] else []
            case_id = recording[3]

            if not defendants_list and not witnesses_list:
                self.failed_imports.append({
                    'table_name': 'booking_participant',
                    'table_id': None,
                    'recording_id': recording_id,
                    'case_id': case_id,
                    'details': f"No defendants and witnesses associated with recording."
                })
                continue
                
            for participant_id in (defendants_list + witnesses_list):
                if not check_existing_record(destination_cursor, 'cases', 'id', case_id):
                    self.failed_imports.append({
                        'table_name': 'booking_participant',
                        'table_id': participant_id,
                        'recording_id': recording_id,
                        'case_id': case_id,
                        'details': f"Case ID associated with participant: {participant_id}, not found in the cases table."
                    })
                    continue
                result = self.get_booking_id(destination_cursor, recording_id)
                
                if result :
                    booking_id = result[0]
                    scheduled_for_date = result[1]
                else:
                    continue

                if not check_existing_record(destination_cursor, 'bookings', 'id', booking_id) or scheduled_for_date is None:
                    self.failed_imports.append({
                        'table_name': 'booking_participant',
                        'table_id': None,
                        'recording_id': recording_id,
                        'case_id': case_id,
                        'details': f"Booking id: {booking_id} associated with participant:{participant_id}, not found in the bookings table."
                    })
                    continue
                
                if participant_id in participant_ids: 
                    try:
                        destination_cursor.execute(
                            """
                            INSERT INTO public.booking_participant (participant_id, booking_id)
                            SELECT %s, %s
                            WHERE NOT EXISTS (
                                SELECT 1
                                FROM public.booking_participant
                                WHERE participant_id = %s AND booking_id = %s
                            )
                            """,
                            (participant_id, booking_id,participant_id, booking_id),
                        )
                        destination_cursor.connection.commit()
                    except Exception as e:
                        self.failed_imports.append({'table_name': 'booking_participant', 'table_id': participant_id, 'details': f"str(e) "} )
                else:
                    self.failed_imports.append({
                        'table_name': 'booking_participant',
                        'table_id': participant_id,
                        'recording_id': recording_id,
                        'case_id': case_id,
                        'details': f"Participant ID: {participant_id} not found in the participants table."
                    })
                
        self.logger.log_failed_imports(self.failed_imports)
