-- Update the location code for 'Exeter Crown Court'
UPDATE public.courts
SET location_code = '423'
WHERE name = 'Exeter Crown Court';

-- Update the location code for 'Kingston-upon-Thames Crown Court'
UPDATE public.courts
SET location_code = '427'
WHERE name = 'Kingston-upon-Thames Crown Court';

-- Enable the pgcrypto extension to allow the generation of UUIDs across all dbs.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Insert new courts into the 'courts' table
-- Each court has a unique UUID generated using gen_random_uuid()
INSERT INTO public.courts (id, court_type, name, location_code)
VALUES
(gen_random_uuid(), 'CROWN', 'Central Criminal Court', '413'),
(gen_random_uuid(), 'CROWN', 'Croydon Crown Court', '418'),
(gen_random_uuid(), 'CROWN', 'Inner London Sessions House Crown Court', '440'),
(gen_random_uuid(), 'CROWN', 'Snaresbrook Crown Court', '453'),
(gen_random_uuid(), 'CROWN', 'Harrow Crown Court', '468'),
(gen_random_uuid(), 'CROWN', 'Wood Green Crown Court', '469'),
(gen_random_uuid(), 'CROWN', 'Southwark Crown Court', '471'),
(gen_random_uuid(), 'CROWN', 'Woolwich Crown Court', '472'),
(gen_random_uuid(), 'CROWN', 'Isleworth Crown Court', '475'),
(gen_random_uuid(), 'CROWN', 'Coventry Combined Court Centre', '417'),
(gen_random_uuid(), 'CROWN', 'Northampton Combined Court', '442'),
(gen_random_uuid(), 'CROWN', 'Shrewsbury Crown Court', '452'),
(gen_random_uuid(), 'CROWN', 'Stafford Combined Crown Court', '455'),
(gen_random_uuid(), 'CROWN', 'Stoke-on-Trent Crown Court', '456'),
(gen_random_uuid(), 'CROWN', 'Warwickshire (South) Justice Centre', '463'),
(gen_random_uuid(), 'CROWN', 'Worcester Combined Court', '466'),
(gen_random_uuid(), 'CROWN', 'Derby Combined Centre', '419'),
(gen_random_uuid(), 'CROWN', 'Wolverhampton Combined Court Centre', '421'),
(gen_random_uuid(), 'CROWN', 'Leicester Crown Court', '430'),
(gen_random_uuid(), 'CROWN', 'Lincoln Crown Court', '432'),
(gen_random_uuid(), 'CROWN', 'Bradford Crown Court', '402'),
(gen_random_uuid(), 'CROWN', 'Doncaster Crown Court (Doncaster Justice Centre South)', '420'),
(gen_random_uuid(), 'CROWN', 'Grimsby Combined Court Centre', '425'),
(gen_random_uuid(), 'CROWN', 'Newcastle Moot Hall Annex', '439'),
(gen_random_uuid(), 'CROWN', 'Newcastle upon Tyne Combined Court Centre', NULL),
(gen_random_uuid(), 'CROWN', 'Sheffield Combined Court Centre', '451'),
(gen_random_uuid(), 'CROWN', 'Teesside Combined Court Centre', '460'),
(gen_random_uuid(), 'CROWN', 'York Crown Court', '467'),
(gen_random_uuid(), 'CROWN', 'Kingston-upon-Hull Combined Court Centre', '766'),
(gen_random_uuid(), 'CROWN', 'Burnley Combined Court Centre', '409'),
(gen_random_uuid(), 'CROWN', 'Carlisle Combined Court Centre', '412'),
(gen_random_uuid(), 'CROWN', 'Chester Crown Court', '415'),
(gen_random_uuid(), 'CROWN', 'Lancaster Crown Court', '409'),
(gen_random_uuid(), 'CROWN', 'Manchester Crown Court (Crown Square)', '435'),
(gen_random_uuid(), 'CROWN', 'Manchester Crown Court (Minshull Street)', '436'),
(gen_random_uuid(), 'CROWN', 'Warrington Crown Court', '462'),
(gen_random_uuid(), 'CROWN', 'Bolton Combined Court', '470'),
(gen_random_uuid(), 'CROWN', 'Aylesbury Crown Court', '401'),
(gen_random_uuid(), 'CROWN', 'Cambridge Crown Court', '410'),
(gen_random_uuid(), 'CROWN', 'Chelmsford Crown Court', '414'),
(gen_random_uuid(), 'CROWN', 'Ipswich Crown Court', '426'),
(gen_random_uuid(), 'CROWN', 'Lewes Combined Court', '431'),
(gen_random_uuid(), 'CROWN', 'Maidstone Combined Court Centre', '434'),
(gen_random_uuid(), 'CROWN', 'Norwich Combined Court Centre', '443'),
(gen_random_uuid(), 'CROWN', 'Oxford Combined Court Centre', '445'),
(gen_random_uuid(), 'CROWN', 'St. Albans Crown Court', '450'),
(gen_random_uuid(), 'CROWN', 'Basildon Combined Court', '461'),
(gen_random_uuid(), 'CROWN', 'Peterborough Combined Court Centre', '473'),
(gen_random_uuid(), 'CROWN', 'Guildford Crown Court', '474'),
(gen_random_uuid(), 'CROWN', 'Luton Crown Court', '476'),
(gen_random_uuid(), 'CROWN', 'Canterbury Combined Court', '479'),
(gen_random_uuid(), 'CROWN', 'King''s Lynn Crown Court', '765'),
(gen_random_uuid(), 'CROWN', 'Bournemouth Combined Court', '406'),
(gen_random_uuid(), 'CROWN', 'Bristol Crown Court', '408'),
(gen_random_uuid(), 'CROWN', 'Gloucester Crown Court', '424'),
(gen_random_uuid(), 'CROWN', 'Plymouth Combined Court', '446'),
(gen_random_uuid(), 'CROWN', 'Portsmouth Combined Court Centre', '447'),
(gen_random_uuid(), 'CROWN', 'Southampton Combined Court Centre', '454'),
(gen_random_uuid(), 'CROWN', 'Swindon Combined Court', '458'),
(gen_random_uuid(), 'CROWN', 'Taunton Combined Court', '459'),
(gen_random_uuid(), 'CROWN', 'Winchester Combined Court Centre', '465'),
(gen_random_uuid(), 'CROWN', 'Truro Combined Court', '477'),
(gen_random_uuid(), 'CROWN', 'Salisbury Law Courts', '480'),
(gen_random_uuid(), 'CROWN', 'Cardiff Crown Court', '411'),
(gen_random_uuid(), 'CROWN', 'Merthyr Tydfil Crown Court', '437'),
(gen_random_uuid(), 'CROWN', 'Newport (South Wales) Crown Court', '441'),
(gen_random_uuid(), 'CROWN', 'Swansea Crown Court', '457'),
(gen_random_uuid(), 'CROWN', 'Caernarfon Justice Centre', '755'),
(gen_random_uuid(), 'CROWN','Hereford Crown Court','762'),
(gen_random_uuid(), 'CROWN','Isle of Wight Combined (and Magistrates) Court','478'),
(gen_random_uuid(), 'CROWN','Preston Combined Court Centre','448');

-- Insert court-region associations into 'court_region'
-- Fetch court_id and region_id based on matching location codes and region names
INSERT INTO PUBLIC.court_region (court_id, region_id)
SELECT c.id AS court_id, r.id AS region_id
FROM PUBLIC.courts c
JOIN PUBLIC.regions r
ON CASE
	WHEN c.location_code IN ( '413', '418', '440', '453', '468', '469', '471', '472', '475' ) THEN r.NAME = 'London'
    WHEN c.location_code IN ( '417', '452', '455', '456', '463', '466', '421','762' ) THEN r.NAME = 'West Midlands (England)'
    WHEN c.location_code IN ( '442', '419', '430', '432' ) THEN r.NAME = 'East Midlands (England)'
	WHEN c.location_code IN ( '409', '412', '415', '435', '436', '448', '462', '470' ) THEN r.NAME = 'North West (England)'
    WHEN c.location_code IN ( '401', '431', '434', '445', '474', '479', '447', '454', '465' ) THEN r.NAME = 'South East (England)'
    WHEN c.location_code IN ( '406', '408', '424', '446', '458', '459', '477', '480','478' ) THEN r.NAME = 'South West (England)'
    WHEN c.location_code IN ( '439', '460', '467' ) THEN r.NAME = 'North East (England)'
    WHEN c.location_code IN ( '411', '437', '441', '457', '755' )  THEN r.NAME = 'Wales'
    WHEN c.location_code IN ( '410', '414', '426', '443', '450', '461', '473', '476', '765' ) THEN r.NAME = 'East of England'
    WHEN c.location_code IN ( '402', '420', '425', '451', '766' ) THEN r.NAME = 'Yorkshire and The Humber'
END; 

-- Generate audit entries for all new courts.
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
WHERE (c.location_code = '409' AND c.name = 'Burnley Combined Court Centre')
   OR (c.location_code = '409' AND c.name = 'Lancaster Crown Court')
   OR (c.name = 'Newcastle upon Tyne Combined Court Centre')
   OR c.location_code IN (
    '401', '402', '406', '408', '410', '411', '412', '413',
	'414', '415', '417', '418', '419', '420', '421', '424', '425', '426',
	'430', '432', '434', '435', '436', '437', '439', '440', '431',
	'441', '442', '443', '445', '446', '447', '448', '450', '451', '452',
	'453', '454', '455', '456', '457', '458', '459', '460', '461', '462',
	'463', '465', '466', '467', '468', '469', '470', '471', '472', '473',
	'474', '475', '476', '477', '478', '479', '480', '755', '765', '766',
    '762'
);

