from datetime import datetime

class RecordCounter:
    def __init__(self, source_conn, destination_conn, file_path='failed_imports_log.txt', count_record_log='counting_records_log.txt' ):
        self.source_conn = source_conn
        self.destination_conn = destination_conn
        self.file_path = file_path
        self.count_record_log = count_record_log
        self.total_migration_time = 0
        self.table_mapping = {
            'recordings': 'recordings',
            'portal_access' : 'portal_access',
            'audits': 'audits',
            'courts': 'courts',
            'regions':'regions',
            'rooms':'rooms',
            'contacts': 'participants',
            'bookings':'bookings',
            'cases': 'cases',
            'roles':'roles',
            'permissions': 'permissions',
            'users': 'users',
            'app_access':'app_access',
            'capture_sessions':'capture_sessions',
            'invites':'invites'
        }

    def fetch_source_data(self, query):
        cursor = self.source_conn.cursor()
        cursor.execute(query)
        data = cursor.fetchone()[0]
        cursor.close()
        return data

    def count_records_in_source_tables(self):
        table_counts = {}

        #Rooms
        table_counts['rooms'] = self.fetch_source_data("SELECT COUNT(*) FROM public.rooms")
        #Users
        table_counts['users'] = self.fetch_source_data("SELECT COUNT(*) FROM public.users")
        # Roles
        table_counts['roles'] = self.fetch_source_data("SELECT COUNT(grouptype) FROM public.grouplist WHERE grouptype = 'Security'")
        # Courts
        table_counts['courts'] = self.fetch_source_data("SELECT COUNT(grouptype) FROM public.grouplist WHERE grouptype = 'Location'")
        #Regions
        table_counts['regions'] = 10
        # Portal Access
        table_counts['portal_access'] = self.fetch_source_data("""
                            SELECT COUNT(*) AS count_result FROM (
                                SELECT
                                    u.userid,
                                    MAX(u.status) AS active,
                                    MAX(u.loginenabled) AS loginenabled,
                                    MAX(u.invited) AS invited,
                                    MAX(u.emailconfirmed) AS emailconfirmed,
                                    MAX(ga.assigned) AS created,
                                    MAX(ga.assignedby) AS createdby
                                FROM public.users u
                                JOIN public.groupassignments ga ON u.userid = ga.userid
                                JOIN public.grouplist gl ON ga.groupid = gl.groupid
                                WHERE gl.groupname = 'Level 3' OR gl.groupname = 'Super User' OR u.invited ILIKE 'true'
                                GROUP BY u.userid
                            ) AS count""")        
        # app access
        table_counts['app_access'] = self.fetch_source_data("""
                            SELECT COUNT(*) AS count_result FROM (
                                SELECT 
                                    u.userid,
                                    MAX(CASE WHEN gl.grouptype = 'Security' THEN ga.groupid ELSE NULL END) AS role_id,
                                    MAX(CASE WHEN gl.grouptype = 'Location' THEN ga.groupid ELSE NULL END) AS court_id,
                                    MAX(u.status) as active,
                                    MAX(ga.assigned) AS created,
                                    MAX(ga.assignedby) AS createdby,
                                    MAX(ga.gaid) AS app_access_id
                                FROM public.users u
                                JOIN public.groupassignments ga ON u.userid = ga.userid
                                JOIN public.grouplist gl ON ga.groupid = gl.groupid
                                WHERE gl.groupname != 'Level 3' AND (gl.grouptype = 'Security' OR gl.grouptype = 'Location')
                                GROUP BY u.userid
                            ) AS count """)
        # cases
        table_counts['cases'] = self.fetch_source_data("SELECT COUNT(*) FROM public.cases")
        #bookings 
        table_counts['bookings'] = self.fetch_source_data("SELECT COUNT(*) FROM public.recordings WHERE parentrecuid = recordinguid and recordingversion = '1'")
        #participants 
        table_counts['contacts'] = self.fetch_source_data("SELECT COUNT(*) FROM public.contacts")
        # capture_sessions 
        table_counts['capture_sessions'] = self.fetch_source_data("SELECT COUNT (DISTINCT parentrecuid) FROM public.recordings WHERE recordingversion = '1' and recordingstatus != 'No Recording'")        
        # recordings 
        table_counts['recordings'] = self.fetch_source_data("SELECT COUNT(*) FROM public.recordings WHERE (recordingavailable IS NULL OR recordingavailable NOT ILIKE 'false' AND recordingavailable NOT ILIKE 'no')")        
        # audits 
        table_counts['audits'] = self.fetch_source_data("SELECT COUNT(*) FROM public.audits")

        return table_counts
       
    def count_records_in_destination_tables(self):
        cursor = self.destination_conn.cursor()
        cursor.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name != 'temp_recordings'")
        tables = cursor.fetchall()

        table_counts = {}
        for table in tables:
            table_name = table[0]
            cursor.execute(f"SELECT COUNT(*) FROM public.{table_name}")
            count = cursor.fetchone()[0]
            table_counts[table_name] = count

        cursor.close()
        return table_counts

    def count_failed_imports(self):
        table_counts = {}
        with open(self.file_path, 'r') as file:
            for line in file:
                split_line = line.split(', ')
                if len(split_line) >= 2:
                    table_name = split_line[0].split(': ')[1].strip()
                
                if table_name in table_counts:
                    table_counts[table_name] += 1
                else:
                    table_counts[table_name] = 1
        return table_counts

    def print_summary(self):
        print(f"| {'Table Name'.ljust(20)} | {'Source DB Records'.ljust(18)} | {'Destination DB Records'.ljust(26)} | {'Failed Imports Logs'.ljust(19)}  ")
        print(f"| {'------------'.ljust(20)} | {'------------------'.ljust(18)} | {'------------------'.ljust(26)} | {'---------------'.ljust(19)}  ")

        source_table_counts = self.count_records_in_source_tables()
        destination_table_counts = self.count_records_in_destination_tables()
        failed_import_counts = self.count_failed_imports()

        for source_table, destination_table in self.table_mapping.items():
            source_records = source_table_counts.get(source_table, '-')
            destination_records = destination_table_counts.get(destination_table, '-')
            failed_import_count = failed_import_counts.get(source_table, '-')
        
            print(f"| {destination_table.ljust(20)} | {str(source_records).ljust(18)} | {str(destination_records).ljust(26)} | {str(failed_import_count).ljust(19)}")

    def log_records_count(self, total_migration_time):
        self.total_migration_time = total_migration_time
        destination_counts = self.count_records_in_destination_tables()
        failed_imports_counts = self.count_failed_imports()

        total_destination_db_count = sum(destination_counts.values())
        total_failed_records = sum(failed_imports_counts.values())
        date_and_time = datetime.now()

        with open(self.count_record_log, 'a') as file: 
            file.write(f"Destination db table count: {total_destination_db_count}, Failed migration log count: {total_failed_records}, Date/Time script run: {date_and_time}, Total migration time: {total_migration_time} seconds.\n")



