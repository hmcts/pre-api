ALTER TABLE public.cases
    DROP CONSTRAINT IF EXISTS check_closed_at_not_in_future,
    DROP CONSTRAINT IF EXISTS check_closed_at_not_null_when_not_open;

ALTER TABLE public.cases
    ALTER COLUMN closed_at TYPE TIMESTAMP;
