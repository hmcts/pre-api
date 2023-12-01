from .helpers import check_existing_record, audit_entry_creation
import uuid

class CourtRegionManager:
    def migrate_data(self,destination_cursor):
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


            if not check_existing_record(destination_cursor,'court_region', 'court_id', court_id):
                id = str(uuid.uuid4())

                destination_cursor.execute(
                    "INSERT INTO public.court_region (id, court_id, region_id) VALUES (%s, %s, %s)",
                    (id, court_id, region_id)
                )

                destination_cursor.connection.commit()

                audit_entry_creation(
                    destination_cursor,
                    table_name="court_region",
                    record_id=id,
                    record=court_id,
                )