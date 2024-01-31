ALTER TABLE public.audits DROP COLUMN type;

CREATE INDEX audit_table_record_id_table_name ON public.audits (table_record_id, table_name);

UPDATE public.audits SET audit_details = CONCAT('{"description": "', audit_details, '"}') WHERE audit_details IS NOT NULL;

ALTER TABLE public.audits ALTER COLUMN audit_details TYPE JSONB USING audit_details::jsonb;

DROP TYPE public.AUDIT_LOG_TYPE;
