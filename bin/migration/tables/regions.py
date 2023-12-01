from .helpers import check_existing_record, audit_entry_creation
import uuid

class RegionManager:
    def migrate_data(self,destination_cursor):
        # Regions data -  https://cjscommonplatform-my.sharepoint.com/:x:/r/personal/lawrie_baber-scovell2_hmcts_net/_layouts/15/Doc.aspx?sourcedoc=%7B07C83A7F-EF01-4C78-9B02-AEDD443D15A1%7D&file=Courts%20PRE%20NRO.xlsx&wdOrigin=TEAMS-WEB.undefined_ns.rwc&action=default&mobileredirect=true
        region_data = [
            'London',
            'West Midlands (England)',
            'West Midlands (England)',
            'East Midlands (England)',
            'Yorkshire and The Humber',
            'North East (England)',
            'North West (England)',
            'South East (England)',
            'East of England',
            'South West (England)',
            'Wales'
        ]

        for region in region_data:
            if not check_existing_record(destination_cursor,'regions', 'name', region):
                id = str(uuid.uuid4())

                destination_cursor.execute(
                    "INSERT INTO public.regions (id, name) VALUES (%s, %s)",
                    (id, region)
                )

                destination_cursor.connection.commit()

                audit_entry_creation(
                    destination_cursor,
                    table_name="regions",
                    record_id=id,
                    record=region,
                )

