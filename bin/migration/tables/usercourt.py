from .helpers import check_existing_record 

class UserCourtManager:
    def __init__(self, source_cursor, logger):
        self.source_cursor = source_cursor
        self.failed_imports = []
        self.logger = logger

    def get_app_access_data(self, destination_cursor):
        query = "SELECT id,court_id,role_id FROM public.app_access"
        destination_cursor.execute(query)
        return destination_cursor.fetchall()
    
    def migrate_data(self, destination_cursor):
        batch_user_court_data = []
        
        app_access_data = self.get_app_access_data(destination_cursor)
        if app_access_data is not None: 
            for data in app_access_data:
                user_id = data[0]
                court_id = data[1]
                role_id = data[2]
                default_court = True

                if not check_existing_record(destination_cursor, 'user_court', 'user_id', user_id):
                    batch_user_court_data.append((user_id, court_id,default_court,role_id))
            
            if batch_user_court_data:
                try:
                    destination_cursor.executemany(
                        """
                        INSERT INTO public.user_court
                            (user_id, court_id,default_court,role_id)
                        VALUES (%s, %s,%s, %s)
                        """,
                        batch_user_court_data,
                    )
                    destination_cursor.connection.commit()

                except Exception as e:
                    self.failed_imports.append({'table_name': 'user_court', 'table_id': None, 'details': str(e)})

        if self.logger:
            self.logger.log_failed_imports(self.failed_imports) 
                

