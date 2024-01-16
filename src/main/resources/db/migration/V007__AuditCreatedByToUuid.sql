
ALTER TABLE public.audits ALTER COLUMN created_by TYPE UUID USING created_by::uuid;
