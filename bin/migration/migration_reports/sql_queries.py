source_table_queries = {
    'rooms': "SELECT COUNT(*) FROM public.rooms",
    'users': "SELECT COUNT(*) FROM public.users",
    'roles': "SELECT COUNT(grouptype) FROM public.grouplist WHERE grouptype = 'Security'",
    'courts': "SELECT COUNT(grouptype) + 1 FROM public.grouplist WHERE grouptype = 'Location'" , # adds 1 for default court
    'regions': "SELECT 12",
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


