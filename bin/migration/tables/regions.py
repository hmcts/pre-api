from .helpers import check_existing_record, audit_entry_creation
import uuid

class RegionManager:
    def __init__(self, logger):
        self.failed_imports = set()
        self.logger = logger

    def migrate_data(self,destination_cursor):
        batch_region_data = []
        # Regions data -  https://cjscommonplatform-my.sharepoint.com/:x:/r/personal/lawrie_baber-scovell2_hmcts_net/_layouts/15/Doc.aspx?sourcedoc=%7B07C83A7F-EF01-4C78-9B02-AEDD443D15A1%7D&file=Courts%20PRE%20NRO.xlsx&wdOrigin=TEAMS-WEB.undefined_ns.rwc&action=default&mobileredirect=true
        region_data = [
            'London',
            'West Midlands (England)',
            'East Midlands (England)',
            'Yorkshire and The Humber',
            'North East (England)',
            'North West (England)',
            'South East (England)',
            'East of England',
            'South West (England)',
            'Wales',
            'Test'
        ]

        for region in region_data:
            if not check_existing_record(destination_cursor,'regions', 'name', region):
                id = str(uuid.uuid4())
                batch_region_data.append((id, region))

        try:
            if batch_region_data:
                destination_cursor.executemany(
                    "INSERT INTO public.regions (id, name) VALUES (%s, %s)",
                    batch_region_data
                )

                destination_cursor.connection.commit()
                
                for entry in batch_region_data:
                    audit_entry_creation(
                        destination_cursor,
                        table_name="regions",
                        record_id=entry[0],
                        record=entry[1],
                    )
        except Exception as e:
            self.failed_imports.add(('regions', batch_region_data[0], e))
 
        self.logger.log_failed_imports(self.failed_imports)
            

