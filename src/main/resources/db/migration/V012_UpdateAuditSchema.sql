ALTER TABLE public.audit DROP COLUMN table_name;
ALTER TABLE public.audit DROP COLUMN table_record_id;
ALTER TABLE public.audit DROP COLUMN type;

ALTER TABLE public.audit ALTER COLUMN audit_details TYPE JSONB;

DROP TYPE public.AUDIT_LOG_TYPE;
