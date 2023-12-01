import uuid
from .helpers import audit_entry_creation, check_existing_record


class CourtManager:
    def __init__(self, source_cursor):
        self.source_cursor = source_cursor

    def get_data(self):
        self.source_cursor.execute("SELECT * from public.courts")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_courts_data):
        # Courts data -  https://cjscommonplatform-my.sharepoint.com/:x:/r/personal/lawrie_baber-scovell2_hmcts_net/_layouts/15/Doc.aspx?sourcedoc=%7B07C83A7F-EF01-4C78-9B02-AEDD443D15A1%7D&file=Courts%20PRE%20NRO.xlsx&wdOrigin=TEAMS-WEB.undefined_ns.rwc&action=default&mobileredirect=true
        court_types = {
<<<<<<< HEAD
            'Reading Crown Court': ('crown','449','UKJ-South East (England)'),
            'Nottingham Crown Court': ('crown','444','UKF-East Midlands (England)'),
            'Mold Crown Court': ('crown','438','UKL-Wales'),
            'Liverpool Crown Court': ('crown','433','UKD-North West (England)'),
            'Leeds Youth Court': ('magistrate','429','UKE-Yorkshire and The Humber'),
            'Leeds Crown Court': ('crown','429','UKE-Yorkshire and The Humber'),
            'Kingston-upon-Thames Crown Court': ('crown','427','UKI-London'),
            'Durham Crown Court': ('crown','422','UKC-North East (England)'),
            'Birmingham': ('crown','404','UKG-West Midlands (England)')
=======
            'Reading Crown Court': ('CROWN','449','UKJ-South East (England)'),
            'Nottingham Crown Court': ('CROWN','444','UKF-East Midlands (England)'),
            'Mold Crown Court': ('CROWN','438','UKL-Wales'),
            'Liverpool Crown Court': ('CROWN','433','UKD-North West (England)'),
            'Leeds Youth Court': ('MAGISTRATE','429','UKE-Yorkshire and The Humber'),
            'Leeds Crown Court': ('CROWN','429','UKE-Yorkshire and The Humber'),
            'Kingston-upon-Thames Crown Court': ('CROWN','427','UKI-London'),
            'Durham Crown Court': ('CROWN','422','UKC-North East (England)'),
            'Birmingham': ('CROWN','404','UKG-West Midlands (England)')
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)
        }
        batch_courts_data = []

        for court in source_courts_data:
            id = court[0]
<<<<<<< HEAD
            court_info = court_types.get(court[1], ('crown', 'Unknown', 'Unknown'))
=======
            court_info = court_types.get(court[1], ('CROWN', 'Unknown', 'Unknown'))
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)
            court_type, location_code, _ = court_info
            name = court[1]

            if not check_existing_record(destination_cursor,'courts', 'id', id ):
<<<<<<< HEAD
                batch_courts_data.append((id, court_type, name, location_code))
=======
                batch_courts_data.append((id, court_type.upper(), name, location_code))
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)

        if batch_courts_data:
            destination_cursor.executemany(
                'INSERT INTO public.courts (id, court_type, name, location_code) VALUES (%s, %s, %s, %s)',
                batch_courts_data
            )

            for court in batch_courts_data:
                audit_entry_creation(
                    destination_cursor,
                    table_name='courts',
                    record_id=court[0],
                    record=court[2]
                )

        # Inserting an 'Unknown' court type for records missing this info
        default_court_id = str(uuid.uuid4())

        try:
            if not check_existing_record(destination_cursor,'courts', 'name', 'Default Court'):
                default_court_id =str(uuid.uuid4())
                destination_cursor.execute(
                    'INSERT INTO public.courts (id, court_type, name, location_code) VALUES (%s, %s, %s, %s)',
<<<<<<< HEAD
                    (default_court_id, 'crown', 'Default Court', 'default'),
=======
                    (default_court_id, 'CROWN', 'Default Court', 'default'),
>>>>>>> 90b5173 (converted enum types to uppercase, added in exceptions and functions for failed imports on some tables and added in scripts to count records)
                )
                destination_cursor.connection.commit()

                audit_entry_creation(
                    destination_cursor,
                    table_name='courts',
                    record_id=default_court_id,
                    record='Default Court'
                ) 
        except Exception as e:
            print(f"Error during insertion: {e}")
