ALTER TABLE public.audits DROP COLUMN type;

DROP TYPE public.AUDIT_LOG_TYPE;

CREATE INDEX audit_table_record_id_table_name ON public.audits (table_record_id, table_name);

ALTER TABLE public.audits ADD COLUMN audit_details_jsonb JSONB;

UPDATE public.audits set audit_details_jsonb = to_jsonb(audit_details::text);

ALTER TABLE public.audits DROP COLUMN audit_details;

ALTER TABLE public.audits RENAME COLUMN audit_details_jsonb TO audit_details;
