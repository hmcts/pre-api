DROP TABLE public.invites;
ALTER TABLE public.portal_access ADD COLUMN invite_code VARCHAR(255) NULL DEFAULT NULL;
