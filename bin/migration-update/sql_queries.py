sql = {
    'users': """SELECT u1.userid AS user_id, u2.userid AS created_by
                FROM public.users u1
                JOIN public.users u2 ON u1.createdby = u2.email;""",
                
    'app_access': """SELECT gaid as app_access_id, assignedby as createdby
                     FROM public.groupassignments ga;"""
}