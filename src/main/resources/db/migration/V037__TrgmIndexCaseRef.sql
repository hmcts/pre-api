CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- index already exists on stg for testing purposes, so we check if it exists before creating it
DO $$
  BEGIN
    IF NOT EXISTS (
      SELECT 1
      FROM pg_indexes
      WHERE schemaname = 'public'
        AND tablename = 'cases'
        AND indexname = 'idx_cases_reference_trgm'
    ) THEN
      EXECUTE 'CREATE INDEX CONCURRENTLY idx_cases_reference_trgm ON public.cases USING gin (reference gin_trgm_ops)';
    END IF;
  END$$;
