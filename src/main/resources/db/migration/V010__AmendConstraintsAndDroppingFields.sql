-- These two defaults were dropped in V007 but we need them back in
ALTER TABLE public.cases ALTER COLUMN modified_at SET DEFAULT NOW();
ALTER TABLE public.bookings ALTER COLUMN modified_at SET DEFAULT NOW();

-- Following discussions these two fields to be dropped
ALTER TABLE public.audits DROP COLUMN updated_at;
ALTER TABLE public.audits DROP COLUMN deleted_at;