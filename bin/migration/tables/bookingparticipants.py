from .helpers import log_failed_imports, check_existing_record

class BookingParticipantManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()
    
    def get_data(self):
        self.source_cursor.execute("SELECT recordinguid, defendants, witnessnames FROM recordings")
        return self.source_cursor.fetchall()
    
    
    def migrate_data(self, destination_cursor, source_data):
        destination_cursor.execute("SELECT id FROM public.participants")
        participant_ids = [row[0] for row in destination_cursor.fetchall()]

        for recording in source_data:
            recording_id = recording[0]
            defendants_list = recording[1].split(',') if recording[1] else []
            witnesses_list = recording[2].split(',') if recording[2] else []

            destination_cursor.execute("""
                SELECT recording_id, booking_id
                FROM public.temp_recordings
                WHERE recording_id = %s 
            """, (recording_id,))
            result = destination_cursor.fetchone()

            if result is not None and len(result) > 0:
                booking_id = result[1]

                for participant_id in (defendants_list + witnesses_list):
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
                                (participant_id, booking_id, participant_id, booking_id),
                            )
                            destination_cursor.connection.commit()
                                        
                        except Exception as e:  
                            self.failed_imports.add(('booking_participants', None, e))
                    else:
                        self.failed_imports.add(('booking_participants', participant_id, "Participant ID not found"))
                
        log_failed_imports(self.failed_imports)
                
