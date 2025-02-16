-- Leeds Crown Court - Updated name to "Leeds Combined Court Centre"
UPDATE public.courts
SET name = 'Leeds Combined Court Centre'
WHERE name = 'Leeds Crown Court';

-- Newcastle upon Tyne Combined Court Centre - Updated location code to 439
UPDATE public.courts
SET location_code = 439
WHERE name = 'Newcastle upon Tyne Combined Court Centre';

-- Leeds' Magistrates' Court (Leeds Youth Court) - Updated name & location code to 2375
UPDATE public.courts
SET 
    name = 'Leeds Magistrates'' Court',
    location_code = 2375
WHERE name = 'Leeds Youth Court';

-- Kingston-upon-Thames Crown Court - Updated location code to 427
UPDATE public.courts
SET location_code = 427
WHERE name = 'Kingston-upon-Thames Crown Court';

--Kingston-upon-Thames Crown Court - Updated court region to "London"
--Newcastle upon Tyne Combined Court Centre - Updated court region to "North East (England)"
INSERT INTO public.court_region (court_id, region_id)
SELECT c.id AS court_id, r.id AS region_id
FROM public.courts c
JOIN public.regions r
ON CASE
    WHEN c.name = 'Newcastle upon Tyne Combined Court Centre' THEN r.NAME = 'North East (England)'
    WHEN c.name = 'Kingston-upon-Thames Crown Court' THEN r.NAME = 'London'
END; 

-- Leeds Crown Court, Leeds Youth - Audit entry regarding name update 
INSERT INTO public.audits (
    id, 
    table_name, 
    table_record_id, 
    source, 
    category, 
    activity, 
    functional_area, 
    created_by, 
    created_at, 
    audit_details
)
SELECT
    gen_random_uuid() AS id,
    'courts' AS table_name,
    c.id AS table_record_id,
    'AUTO' AS source,
    'Court' AS category,
    'Update' AS activity,
    'Update a court' AS functional_area,
    (SELECT id FROM public.users WHERE last_name = 'Admin' LIMIT 1) AS created_by,
    NOW() AS created_at,
    jsonb_build_object(
        'description', 'Court name has been updated.',
        'courtName', c.name,
        'locationCode', c.location_code
    ) AS audit_details
FROM public.courts c
WHERE c.name IN (
    'Leeds Combined Court Centre',
    'Leeds Magistrates'' Court'
);

--Audit entry regarding location code update
--Kingston-upon-Thames Crown Court,
--Newcastle upon Tyne Combined Court Centre,
--Leeds Youth 
INSERT INTO public.audits (
    id, 
    table_name, 
    table_record_id, 
    source, 
    category, 
    activity, 
    functional_area, 
    created_by, 
    created_at, 
    audit_details
)
SELECT
    gen_random_uuid() AS id,
    'courts' AS table_name,
    c.id AS table_record_id,
    'AUTO' AS source,
    'Court' AS category,
    'Update' AS activity,
    'Update a court' AS functional_area,
    (SELECT id FROM public.users WHERE last_name = 'Admin' LIMIT 1) AS created_by,
    NOW() AS created_at,
    jsonb_build_object(
        'description', 'Court location code updated',
        'courtName', c.name,
        'locationCode', c.location_code
    ) AS audit_details
FROM public.courts c
WHERE c.name IN (
    'Newcastle upon Tyne Combined Court Centre',
    'Kingston-upon-Thames Crown Court',
    'Leeds Magistrates'' Court'
);
