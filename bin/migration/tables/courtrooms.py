from .helpers import check_existing_record, audit_entry_creation, log_failed_imports
import uuid
import re

class CourtRoomManager:
    def __init__(self):
        self.failed_imports = set()

    def migrate_data(self, destination_cursor):
        # CVP room data - https://tools.hmcts.net/confluence/display/S28/CVP+Guides#CVPGuides-CVPRooms-EnvironmentandCourtAllocation
        courtroom_data = {
            "PRE001": "Leeds Mags Court",
            "PRE002": "Leeds Mags Court",
            "PRE003": "Leeds Mags Court",
            "PRE004": "Mold Crown Court",
            "PRE005": "Mold Crown Court",
            "PRE006": "Leeds Crown Court",
            "PRE007": "Leeds Crown Court",
            "PRE008": "Default Court",
            "PRE009": "Default Court",
            "PRE010": "Default Court",
            "PRE011": "Durham Crown Court",
            "PRE012": "Durham Crown Court",
            "PRE013": "Kingston upon Thames Crown Court",
            "PRE014": "Kingston upon Thames Crown Court",
            "PRE015": "Liverpool Crown Court",
            "PRE016": "Liverpool Crown Court",
            "PRE017": "Nottingham Crown Court",
            "PRE018": "Nottingham Crown Court",
            "PRE019": "Reading Crown Court",
            "PRE020": "Reading Crown Court"
        }

        batch_courtrooms_data = []

        destination_cursor.execute("SELECT * FROM public.rooms")
        dest_rooms_data = destination_cursor.fetchall()
        rooms_dict = {role[1]: role[0] for role in dest_rooms_data} 

        destination_cursor.execute("SELECT * FROM public.courts")
        dest_courts_data = destination_cursor.fetchall()
        court_dict = {court[2]: court[0] for court in dest_courts_data}

        for room, court in courtroom_data.items():
            court_name_pattern = re.compile(rf"{re.escape(court)}", re.IGNORECASE)

            if room in rooms_dict:
                room_id = rooms_dict[room]

                matched_court_ids = [court_id for court_name, court_id in court_dict.items() if re.search(court_name_pattern, court_name)]
                if matched_court_ids:
                    court_id = matched_court_ids[0]

                    if not check_existing_record(destination_cursor,'courtrooms', 'room_id', room_id):
                        batch_courtrooms_data.append((court_id, room_id))

        try:
            if batch_courtrooms_data:

                destination_cursor.executemany(
                    "INSERT INTO public.courtrooms ( court_id, room_id) VALUES ( %s, %s)",
                    batch_courtrooms_data
                )
                destination_cursor.connection.commit()
  
        except Exception as e:
            self.failed_imports.add(('court_rooms', None, e))
        
        log_failed_imports(self.failed_imports)
       
