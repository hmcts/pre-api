BEGIN;

-- Update court names
UPDATE public.courts
SET name = CASE
    WHEN name = 'Bolton Combined Court' THEN 'Bolton Crown Court'
    WHEN name = 'Burnley Combined Court Centre' THEN 'Burnley Combined Court'
    WHEN name = 'Carlisle Combined Court Centre' THEN 'Carlisle Combined Court'
    WHEN name = 'Derby Combined Centre' THEN 'Derby Combined Court'
    WHEN name = 'Exeter Combined Court Centre' THEN 'Exeter Crown Court'
    WHEN name = 'Grimsby Combined Court Centre' THEN 'Great Grimsby Combined Court'
    WHEN name = 'Inner London Sessions House Crown Court' THEN 'Inner London Crown Court'
    WHEN name = 'Kingston-upon-Hull Combined Court Centre' THEN 'Kingston-upon-Hull Combined Court'
    WHEN name = 'Maidstone Combined Court Centre' THEN 'Maidstone Combined Court'
    WHEN name = 'Manchester Crown Court (Crown Square)' THEN 'Manchester Crown Court'
    WHEN name = 'Merthyr Tydfil Crown Court' THEN 'Merthyr Tydfil Combined Court'
    WHEN name = 'Mold Justice Centre (Mold Law Courts)' THEN 'Mold Justice Centre'
    WHEN name = 'Newcastle upon Tyne Combined Court Centre' THEN 'Newcastle upon Tyne Crown Court'
    WHEN name = 'Norwich Combined Court Centre' THEN 'Norwich Combined Court'
    WHEN name = 'Nottingham County Court & Family Court Crown Court' THEN 'Nottingham Crown Court'
    WHEN name = 'Oxford Combined Court Centre' THEN 'Oxford Combined Court'
    WHEN name = 'Peterborough Combined Court Centre' THEN 'Peterborough Combined Court'
    WHEN name = 'Portsmouth Combined Court Centre' THEN 'Portsmouth Combined Court'
    WHEN name = 'Preston Combined Court Centre' THEN 'Preston Combined Court'
    WHEN name = 'Sheffield Combined Court Centre' THEN 'Sheffield Combined Court'
    WHEN name = 'Southampton Combined Court Centre' THEN 'Southampton Combined Court'
    WHEN name = 'Stafford Combined Crown Court' THEN 'Stafford Combined Court'
    WHEN name = 'Stoke-on-Trent Crown Court' THEN 'Stoke-on-Trent Combined Court'
    WHEN name = 'Teesside Combined Court Centre' THEN 'Teesside Combined Court'
    WHEN name = 'Warwickshire (South) Justice Centre' THEN 'Warwick Combined Court'
    WHEN name = 'Winchester Combined Court Centre' THEN 'Winchester Combined Court'
    WHEN name = 'Wolverhampton Combined Court Centre' THEN 'Wolverhampton Combined Court'
    WHEN name = 'Leeds Crown Court' THEN 'Leeds Combined Court Centre'  
    WHEN name = 'Leeds Youth Court' THEN 'Leeds Magistrates'' Court'
    WHEN name = 'Liverpool QEII Law Courts: Liverpool Crown Court' THEN 'Liverpool Crown Court'
    WHEN name = 'Isle of Wight Combined (and Magistrates) Court' THEN 'Isle of Wight Combined Court'
    WHEN name = 'Birmingham Crown Court & Annex' THEN 'Birmingham Crown Court'

    ELSE name
END
WHERE name IN (
    'Bolton Combined Court',
    'Burnley Combined Court Centre',
    'Carlisle Combined Court Centre',
    'Derby Combined Centre',
    'Exeter Combined Court Centre',
    'Grimsby Combined Court Centre',
    'Inner London Sessions House Crown Court',
    'Kingston-upon-Hull Combined Court Centre',
    'Maidstone Combined Court Centre',
    'Manchester Crown Court (Crown Square)',
    'Merthyr Tydfil Crown Court',
    'Mold Justice Centre (Mold Law Courts)',
    'Newcastle upon Tyne Combined Court Centre',
    'Norwich Combined Court Centre',
    'Nottingham County Court & Family Court Crown Court',
    'Oxford Combined Court Centre',
    'Peterborough Combined Court Centre',
    'Portsmouth Combined Court Centre',
    'Preston Combined Court Centre',
    'Sheffield Combined Court Centre',
    'Southampton Combined Court Centre',
    'Stafford Combined Crown Court',
    'Stoke-on-Trent Crown Court',
    'Teesside Combined Court Centre',
    'Warwickshire (South) Justice Centre',
    'Winchester Combined Court Centre',
    'Wolverhampton Combined Court Centre',
    'Leeds Crown Court',
    'Leeds Youth Court',
    'Liverpool QEII Law Courts: Liverpool Crown Court',
    'Isle of Wight Combined (and Magistrates) Court',
    'Birmingham Crown Court & Annex'
);

-- Delete courts where not required
DELETE FROM court_region cr
WHERE cr.court_id IN (
    SELECT c.id 
    FROM courts c
    WHERE c.name IN (
        'Caernarfon Justice Centre', 
        'Coventry Combined Court Centre',
        'Doncaster Crown Court (Doncaster Justice Centre South)',
        'King''s Lynn Crown Court',
        'Lancaster Crown Court',
        'Newcastle Moot Hall Annex',
        'Newport (South Wales) Crown Court',
        'Warrington Crown Court'
    )
    AND c.id NOT IN (SELECT court_id FROM cases)
    AND c.id NOT IN (SELECT court_id FROM app_access)
);

DELETE FROM public.courts
WHERE name IN (
    'Caernarfon Justice Centre', 
    'Coventry Combined Court Centre',
    'Doncaster Crown Court (Doncaster Justice Centre South)',
    'King''s Lynn Crown Court',
    'Lancaster Crown Court',
    'Newcastle Moot Hall Annex',
    'Newport (South Wales) Crown Court',
    'Warrington Crown Court'
)
AND id NOT IN (SELECT court_id FROM cases)
AND id NOT IN (SELECT court_id FROM app_access);


-- Updated location codes
UPDATE public.courts
SET location_code = CASE
    WHEN name = 'Newcastle upon Tyne Crown Court' THEN '439'
    WHEN name = 'Leeds Magistrates'' Court' THEN '2375'
    WHEN name = 'Kingston-upon-Thames Crown Court' THEN '427'
    ELSE location_code
END
WHERE name IN (
    'Newcastle upon Tyne Crown Court',
    'Leeds Magistrates'' Court',
    'Kingston-upon-Thames Crown Court'
);

-- Updated court regions
INSERT INTO public.court_region (court_id, region_id)
SELECT c.id AS court_id, r.id AS region_id
FROM public.courts c
JOIN public.regions r
ON CASE
    WHEN c.name = 'Newcastle upon Tyne Crown Court' THEN r.NAME = 'North East (England)'
    WHEN c.name = 'Kingston-upon-Thames Crown Court' THEN r.NAME = 'London'
END; 

--Audit entry regarding name updates
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
    'Bolton Crown Court',
    'Burnley Combined Court',
    'Carlisle Combined Court',
    'Derby Combined Court',
    'Exeter Crown Court',
    'Great Grimsby Combined Court',
    'Inner London Crown Court',
    'Kingston-upon-Hull Combined Court',
    'Maidstone Combined Court',
    'Manchester Crown Court',
    'Merthyr Tydfil Combined Court',
    'Mold Justice Centre',
    'Newcastle upon Tyne Crown Court',
    'Norwich Combined Court',
    'Nottingham Crown Court',
    'Oxford Combined Court',
    'Peterborough Combined Court',
    'Portsmouth Combined Court',
    'Preston Combined Court',
    'Sheffield Combined Court',
    'Southampton Combined Court',
    'Stafford Combined Court',
    'Stoke-on-Trent Combined Court',
    'Teesside Combined Court',
    'Warwick Combined Court',
    'Winchester Combined Court',
    'Wolverhampton Combined Court',
    'Leeds Combined Court Centre',
    'Leeds Magistrates'' Court',
    'Liverpool Crown Court',
    'Isle of Wight Combined Court',
    'Birmingham Crown Court'
);

--Audit entry regarding location code updates
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
    'Newcastle upon Tyne Crown Court',
    'Kingston-upon-Thames Crown Court',
    'Leeds Magistrates'' Court'
);


COMMIT;
