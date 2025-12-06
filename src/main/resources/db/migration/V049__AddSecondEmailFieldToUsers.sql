ALTER TABLE public.users 
ADD COLUMN alternative_email VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_users_alternative_email ON public.users (alternative_email);