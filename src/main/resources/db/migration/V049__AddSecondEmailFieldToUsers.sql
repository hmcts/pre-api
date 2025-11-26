ALTER TABLE public.users 
ADD COLUMN email2 VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_users_email2 ON public.users (email2);