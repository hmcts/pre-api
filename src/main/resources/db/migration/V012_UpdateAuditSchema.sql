ALTER TABLE public.audit DROP COLUMN type;

CREATE INDEX audit_table_record_id_table_name ON public.audit (table_record_id, table_name);

ALTER TABLE public.audit ALTER COLUMN audit_details TYPE JSONB;

DROP TYPE public.AUDIT_LOG_TYPE;
