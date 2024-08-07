CREATE TYPE public.CASE_STATE AS ENUM (
    'OPEN',
    'PENDING_CLOSURE',
    'CLOSED'
);

ALTER TABLE public.cases
    ADD COLUMN state CASE_STATE,
    ADD COLUMN closed_at TIMESTAMPTZ;

-- Update existing rows with a default state value
UPDATE public.cases
  SET state = 'OPEN'
  WHERE state IS NULL;;

-- Ensure 'state' column has the default value and is not null
ALTER TABLE public.cases
    ALTER COLUMN state SET DEFAULT 'OPEN',
    ALTER COLUMN state SET NOT NULL;

-- Constraint to make sure closed_at is not null when state is not 'OPEN'
ALTER TABLE public.cases
    ADD CONSTRAINT check_closed_at_not_null_when_not_open
    CHECK (state = 'OPEN' OR closed_at IS NOT NULL);
