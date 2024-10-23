DROP TYPE public.ACCESS_STATUS CASCADE;
ALTER TABLE public.portal_access ADD COLUMN terms_accepted_at TIMESTAMPTZ;
ALTER TABLE public.portal_access ADD COLUMN logged_in TIMESTAMPTZ;