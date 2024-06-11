sql = {
    'users': """SELECT u1.userid AS user_id, u2.userid AS created_by
                FROM public.users u1
                JOIN public.users u2 ON u1.createdby = u2.email;""",
                
    'app_access': """SELECT gaid as app_access_id, assignedby as createdby
                     FROM public.groupassignments ga;""",

    'portal_access': """SELECT gaid as groupassignments_id, userid as user_id, assignedby as createdby
                        FROM public.groupassignments ga
                        JOIN public.grouplist gl ON ga.groupid = gl.groupid
                        WHERE gl.groupname = 'Level 3';""",

    'portal_access_api_db': """SELECT id FROM public.portal_access where user_id = %s"""
}