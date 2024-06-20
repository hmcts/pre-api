-- Define CASE_STATE enum
CREATE TYPE public.CASE_STATE AS ENUM (
    'OPEN',
    'PENDING_CLOSURE',
    'CLOSED'
);

-- Alter cases table to include state and closed_at columns
ALTER TABLE public.cases
    ADD COLUMN state CASE_STATE NOT NULL,
    ADD COLUMN closed_at TIMESTAMPTZ;

-- Update existing cases to default state OPEN where deleted_at is NULL
UPDATE public.cases 
SET state = 'OPEN' 
WHERE deleted_at IS NULL;

-- Set the default value of state to OPEN for future inserts
ALTER TABLE public.cases ALTER COLUMN state SET DEFAULT 'OPEN';

-- Constraint to ensure closed_at is not in the future
ALTER TABLE public.cases
    ADD CONSTRAINT check_closed_at_not_in_future CHECK (closed_at <= NOW());

