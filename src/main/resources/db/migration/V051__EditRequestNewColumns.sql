ALTER TABLE public.edit_requests ADD COLUMN jointly_agreed BOOLEAN;
ALTER TABLE public.edit_requests ADD COLUMN rejection_reason VARCHAR(512);
ALTER TABLE public.edit_requests ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE public.edit_requests ADD COLUMN approved_by VARCHAR(100);
