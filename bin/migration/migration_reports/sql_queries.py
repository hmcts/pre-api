source_table_queries = {
    'rooms': "SELECT COUNT(*) FROM public.rooms",
    'users': "SELECT COUNT(*) FROM public.users",
    'roles': "SELECT COUNT(grouptype) FROM public.grouplist WHERE grouptype = 'Security'",
    'courts': "SELECT COUNT(grouptype) + 1 FROM public.grouplist WHERE grouptype = 'Location'" , # adds 1 for default court
    'regions': "",
    'cases': "SELECT COUNT(*) FROM public.cases",
    'bookings':"SELECT COUNT(*) FROM public.recordings WHERE parentrecuid = recordinguid and recordingversion = '1'",
    'contacts':  "SELECT COUNT(*) FROM public.contacts",
    'capture_sessions': "SELECT COUNT(*) FROM public.recordings WHERE parentrecuid = recordinguid AND recordingstatus != 'No Recording' AND NOT (recordingstatus = 'Deleted' AND ingestaddress IS NULL)",
    'recordings': "SELECT COUNT(*) FROM public.recordings WHERE recordingstatus !='No Recording' AND (recordingavailable IS NULL OR recordingavailable LIKE 'true')",
    'audits': "SELECT COUNT(*) FROM public.audits",
    'share_bookings': "SELECT COUNT(*) FROM public.videopermissions",
    'portal_access': """SELECT COUNT(*) AS count_result FROM (
                            SELECT
                                u.userid,
                                u.status as active,
                                u.loginenabled as loginenabled,
                                u.invited as invited,
                                u.emailconfirmed as emailconfirmed,
                                MAX(ga.assigned) AS created,
                                MAX(ga.assignedby) AS createdby
                            FROM public.users u
                            JOIN public.groupassignments ga ON u.userid = ga.userid
                            JOIN public.grouplist gl ON ga.groupid = gl.groupid
                            WHERE gl.groupname = 'Level 3' OR u.invited ILIKE 'true'
                            GROUP BY u.userid
                        ) AS count""",
    'app_access': """SELECT COUNT(*) AS count_result FROM (
                        SELECT 
                            u.userid,
                            COUNT(CASE WHEN gl.grouptype = 'Security' AND ga.groupid IS NOT NULL THEN 1 ELSE NULL END) AS role_id_count,
                            MAX(CASE WHEN gl.grouptype = 'Location' THEN ga.groupid ELSE NULL END) AS court_id,
                            u.status AS active,
                            MAX(ga.assigned) AS created,
                            MAX(ga.assignedby) AS createdby
                        FROM public.users u
                        JOIN public.groupassignments ga ON u.userid = ga.userid
                        JOIN public.grouplist gl ON ga.groupid = gl.groupid
                        WHERE gl.groupname != 'Level 3' AND (gl.grouptype = 'Security' OR gl.grouptype = 'Location')
                        GROUP BY u.userid 
                        HAVING COUNT(CASE WHEN gl.grouptype = 'Security' AND ga.groupid IS NOT NULL THEN 1 ELSE NULL END) > 0
                        ) AS count"""
}

logger_queries = {
    'create_table_query': """CREATE TABLE IF NOT EXISTS public.%s (
                            id SERIAL PRIMARY KEY,
                            table_name VARCHAR(255),
                            table_id UUID,
                            case_id UUID,
                            recording_id UUID,
                            details TEXT
                        )""",

    'insert_query': """ INSERT INTO public.failed_imports
                    (table_name, table_id, case_id, recording_id, details)
                    VALUES (%s, %s, %s, %s, %s)""",

    'update_query': "UPDATE failed_imports SET date_migrated =%s WHERE table_name = %s AND id = %s",

    'existing_record_query': """SELECT EXISTS (SELECT 1 FROM public.failed_imports 
                            WHERE table_name = %s AND table_id = %s AND case_id = %s AND recording_id = %s)""",

    'failed_migration_record_query': "SELECT * FROM public.failed_imports WHERE date_migrated IS NULL"
}

migration_tracker_queries = {
    'count_audit_records_query' : "SELECT COUNT(*) FROM public.audits WHERE table_name != 'audits'",
    'count_failed_migrations_query' : "SELECT COUNT(*) FROM public.failed_imports"
}
