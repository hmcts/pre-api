-- Update the court names in the courts table by replacing the old names with new ones.
UPDATE public.courts
SET name = CASE
    WHEN name = 'Exeter Crown Court' THEN 'Exeter Combined Court Centre'
    WHEN name = 'Liverpool Crown Court' THEN 'Liverpool QEII Law Courts: Liverpool Crown Court'
    WHEN name = 'Mold Crown Court' THEN 'Mold Justice Centre (Mold Law Courts)'
    WHEN name = 'Nottingham Crown Court' THEN 'Nottingham County Court & Family Court Crown Court'
    WHEN name = 'Kingston upon Thames Crown Court' THEN 'Kingston-upon-Thames Crown Court'
    ELSE name  
END
WHERE name IN (
    'Exeter Crown Court',
    'Liverpool Crown Court',
    'Mold Crown Court',
    'Kingston upon Thames Crown Court',
    'Nottingham Crown Court'
);

-- Generate audit entries for court name update.
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
    'Exeter Combined Court Centre',
    'Liverpool QEII Law Courts: Liverpool Crown Court',
    'Mold Justice Centre (Mold Law Courts)',
    'Kingston-upon-Thames Crown Court',
    'Nottingham County Court & Family Court Crown Court'
);

-- Insert new court into the 'courts' table
INSERT INTO public.courts (id, court_type, name, location_code)
VALUES
(gen_random_uuid(), 'CROWN', 'Birmingham Crown Court & Annex', '404');

-- Insert court-region association into 'court_region'
INSERT INTO PUBLIC.court_region (court_id, region_id)
SELECT c.id AS court_id, r.id AS region_id
FROM PUBLIC.courts c
JOIN PUBLIC.regions r
ON CASE
    WHEN c.name = 'Birmingham Crown Court & Annex' THEN r.NAME = 'West Midlands (England)'
END; 

-- Generate audit entry for new court.
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
    'Add' AS activity,
    'Add a court' AS functional_area,
    (SELECT id FROM public.users WHERE last_name = 'Admin' LIMIT 1) AS created_by,
    NOW() AS created_at,
    jsonb_build_object(
        'description', 'A new court has been added.',
        'courtName', c.name,
        'locationCode', c.location_code
    ) AS audit_details
FROM public.courts c
WHERE (c.name = 'Birmingham Crown Court & Annex');
