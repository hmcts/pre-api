ALTER TABLE public.portal_access RENAME COLUMN invitation_datetime TO invited_at;

ALTER TABLE public.portal_access RENAME COLUMN registered_datetime TO registered_at;
