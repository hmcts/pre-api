from .helpers import log_failed_imports

class BookingParticipantManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()
    
    
    def get_participants_for_recording(self, case_id):
        self.source_cursor.execute("""
            SELECT caseuid, defendants, witnessnames 
            FROM public.recordings
            WHERE caseuid = %s
        """, (case_id,))
        columns = ['case_id', 'defendants', 'witnesses']
        return [dict(zip(columns, row)) for row in self.source_cursor.fetchall()]


    def migrate_data(self, destination_cursor):
        # Fetch case_id's for participants
        destination_cursor.execute("""
            SELECT p.id AS participant_id, b.id AS booking_id, p.case_id AS case_id
            FROM public.participants p
            JOIN public.bookings b ON p.case_id = b.case_id
            WHERE NOT EXISTS (
                SELECT 1
                FROM public.booking_participant bp
                WHERE bp.participant_id = p.id AND bp.booking_id = b.id
            )
        """)
        booking_participant_query = destination_cursor.fetchall()
        

        for row in booking_participant_query:
            participant_id, booking_id, case_id = row

            source_data = self.get_participants_for_recording(case_id)
            participant_ids_in_source = list({participant['defendants'] for participant in source_data} | {participant['witnesses'] for participant in source_data}) 

            if participant_id in participant_ids_in_source:
                try: 
                    destination_cursor.execute(
                        """
                        INSERT INTO public.booking_participant (participant_id, booking_id)
                        VALUES (%s, %s)
                        """,
                        (participant_id, booking_id),  
                    )
                    destination_cursor.connection.commit()
                            
                except Exception as e:  
                    self.failed_imports.add(('booking_participants', None, e))
                
        log_failed_imports(self.failed_imports)
                
