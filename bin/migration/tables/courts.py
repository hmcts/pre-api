import uuid
from .helpers import audit_entry_creation, check_existing_record
import re

class CourtManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = set()
        self.logger = logger

    def get_data(self):
        self.source_cursor.execute("SELECT groupid, groupname from public.grouplist where grouptype = 'Location'")
        return self.source_cursor.fetchall()

    def migrate_data(self, destination_cursor, source_courts_data): 
        # Courts data -  https://cjscommonplatform-my.sharepoint.com/:x:/r/personal/lawrie_baber-scovell2_hmcts_net/_layouts/15/Doc.aspx?sourcedoc=%7B07C83A7F-EF01-4C78-9B02-AEDD443D15A1%7D&file=Courts%20PRE%20NRO.xlsx&wdOrigin=TEAMS-WEB.undefined_ns.rwc&action=default&mobileredirect=true
        court_types = {
            'Reading Crown Court': ('CROWN','449','UKJ-South East (England)'),
            'Nottingham Crown Court': ('CROWN','444','UKF-East Midlands (England)'),
            'Mold Crown': ('CROWN','438','UKL-Wales'),
            'Liverpool Crown Court': ('CROWN','433','UKD-North West (England)'),
            'Leeds Youth Court': ('MAGISTRATE','429','UKE-Yorkshire and The Humber'),
            'Leeds Crown Court': ('CROWN','429','UKE-Yorkshire and The Humber'),
            'Kingston upon Thames Crown Court': ('CROWN','427','UKI-London'),
            'Durham Crown Court': ('CROWN','422','UKC-North East (England)'),
            'Birmingham Crown Court': ('CROWN','404','UKG-West Midlands (England)'),
            'Birmingham Youth Court': ('CROWN','404','UKG-West Midlands (England)')
        }
        batch_courts_data = []

        for court in source_courts_data:
            id = court[0]
            name = court[1]
            court_info = None

            for court_pattern, info in court_types.items():
                regex_pattern = re.compile(rf"{re.escape(court_pattern)}(?:\sCourt)?", re.IGNORECASE)
                if re.search(regex_pattern, name):
                    court_info = info
                    break 
                
                if court_info is None:
                    court_info = ('CROWN', '', '')

            court_type, location_code, _ = court_info
            

            if not check_existing_record(destination_cursor,'courts', 'id', id ):
                batch_courts_data.append((id, court_type.upper(), name, location_code))
        
        try:
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
        except Exception as e:
            self.failed_imports.add(('courts', id, e ))

        # Inserting an 'Unknown' court type for records missing this info
        default_court_id = str(uuid.uuid4())

        try:
            if not check_existing_record(destination_cursor,'courts', 'name', 'Default Court'):
                default_court_id =str(uuid.uuid4())
                destination_cursor.execute(
                    'INSERT INTO public.courts (id, court_type, name, location_code) VALUES (%s, %s, %s, %s)',
                    (default_court_id, 'CROWN', 'Default Court', 'default'),
                )
                destination_cursor.connection.commit()

                audit_entry_creation(
                    destination_cursor,
                    table_name='courts',
                    record_id=default_court_id,
                    record='Default Court'
                ) 
        except Exception as e:
            self.failed_imports.add(('courts', default_court_id, e))
 
        self.logger.log_failed_imports(self.failed_imports)
