from .helpers import check_existing_record, audit_entry_creation, log_failed_imports
import uuid

class CourtRegionManager:
    def __init__(self):
        self.failed_imports = set()

    def migrate_data(self,destination_cursor):
        batch_court_region_data = []

        # Court regions data -  https://cjscommonplatform-my.sharepoint.com/:x:/r/personal/lawrie_baber-scovell2_hmcts_net/_layouts/15/Doc.aspx?sourcedoc=%7B07C83A7F-EF01-4C78-9B02-AEDD443D15A1%7D&file=Courts%20PRE%20NRO.xlsx&wdOrigin=TEAMS-WEB.undefined_ns.rwc&action=default&mobileredirect=true
        court_regions = [
            {"name": "Birmingham", "region": "West Midlands (England)"},
            {"name": "Mold Crown Court", "region": "Wales"},
            {"name": "Reading Crown Court", "region": "South East (England)"},
            {"name": "Leeds Crown Court", "region": "Yorkshire and The Humber"},
            {"name": "Durham Crown Court", "region": "North East (England)"},
            {"name": "Liverpool Crown Court", "region": "North West (England)"},
            {"name": "Nottingham Crown Court", "region": "East Midlands (England)"},
            {"name": "Kingston-upon-Thames Crown Court", "region": "London"},
            {"name": "Leeds Youth Court", "region": "Yorkshire and The Humber"},
            {"name": "Default Court", "region": "London"}
        ]
        court_regions_dict = {court["name"]: court["region"] for court in court_regions}

        destination_cursor.execute('SELECT id, name FROM public.courts')
        courts_data = destination_cursor.fetchall()

        destination_cursor.execute('SELECT id, name FROM public.regions')
        regions_data = destination_cursor.fetchall()
        regions_dict = {region[1]: region[0] for region in regions_data}

        for court in courts_data:
            court_id = court[0]
            court_name = court[1]
            region_name = court_regions_dict.get(court_name)
            region_id = regions_dict.get(region_name)

            if region_id is None:
                self.failed_imports.add(('court_region', court_id, 'Missing region_id'))
            else:
                if not check_existing_record(destination_cursor,'court_region', 'court_id', court_id):
                    id = str(uuid.uuid4())
                    batch_court_region_data.append((id, court_id, region_id))

        try: 
            if batch_court_region_data:
                destination_cursor.executemany(
                    "INSERT INTO public.court_region (id, court_id, region_id) VALUES (%s, %s, %s)",
                    batch_court_region_data
                )

                destination_cursor.connection.commit()

                for entry in batch_court_region_data:
                    audit_entry_creation(
                    destination_cursor,
                    table_name="court_region",
                    record_id=entry[0],
                    record=entry[1],
                )

        except Exception as e:  
            self.failed_imports.add(('court_region', id,e))
                    
        log_failed_imports(self.failed_imports)            
            