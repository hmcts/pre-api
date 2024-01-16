from .helpers import log_failed_imports

class BookingParticipantManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor
        self.failed_imports = set()
    
    
    def migrate_data(self, destination_cursor):
        destination_cursor.execute("SELECT id FROM public.bookings")
        booking_data = destination_cursor.fetchall()

        for booking in booking_data:
            destination_cursor.execute("""
                SELECT p.id AS participant_id, b.id AS booking_id, p.case_id AS case_id
                FROM public.participants p
                JOIN public.bookings b ON p.case_id = b.case_id
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM public.booking_participant bp
                    WHERE bp.participant_id = p.id AND bp.booking_id = b.id
                )
                AND b.id = %s
            """, (booking,))
            booking_participant_data = destination_cursor.fetchall()

            for row in booking_participant_data:
                participant_id, booking_id, case_id = row

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
                
