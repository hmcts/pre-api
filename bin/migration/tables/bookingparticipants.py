from .helpers import audit_entry_creation, log_failed_imports
import uuid

class BookingParticipantManager:
    def __init__(self):
        self.failed_imports = set()

    def migrate_data(self, destination_cursor):
        # Fetch case_id's for participants
        destination_cursor.execute("""
            SELECT p.id AS participant_id, b.id AS booking_id
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
            participant_id, booking_id = row
            id = str(uuid.uuid4())

            try: 
                destination_cursor.execute(
                    """
                    INSERT INTO public.booking_participant (id, participant_id, booking_id)
                    VALUES (%s, %s, %s)
                    """,
                    (id, participant_id, booking_id),  
                )
                        
                audit_entry_creation(
                    destination_cursor,
                    table_name="booking_participant",
                    record_id=id,
                    record=booking_id,
                )
            except Exception as e:  
                self.failed_imports.add(('booking_participants',id,e))
                
        log_failed_imports(self.failed_imports)         
                
