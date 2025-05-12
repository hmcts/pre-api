BEGIN;

WITH deleted_courts AS (
    DELETE FROM public.courts
    WHERE name = 'Burnley Combined Court'
      AND id NOT IN (SELECT court_id FROM cases)
      AND id NOT IN (SELECT court_id FROM app_access)
    RETURNING id, name
)

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
    gen_random_uuid(),
    'courts',
    id,
    'AUTO',
    'Court',
    'Delete',
    'Delete a court',
    (SELECT id FROM public.users WHERE last_name = 'Admin' LIMIT 1),
    NOW(),
    jsonb_build_object(
        'description', 'Court has been deleted.',
        'courtName', name
    )
FROM deleted_courts
WHERE EXISTS (SELECT 1 FROM deleted_courts);

COMMIT;

