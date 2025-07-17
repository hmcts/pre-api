CREATE TYPE public.VF_MIGRATION_STATUS AS ENUM (
	'PENDING',   -- default state when a recording is inserted in temp table but not yet processed
	'SUCCESS',   -- recording was processed successfully
	'FAILED',    -- recording failed processing due to validation or system error
  'READY',
	'SUBMITTED'  -- admin has corrected the data and it is ready for reprocessing (once reprocessed to revert to success / failure)
);

CREATE TABLE public.vf_migration_records (
    id UUID PRIMARY KEY,

    -- Metadata fields
    archive_id TEXT NOT NULL,
    archive_name TEXT NOT NULL,
    create_time TIMESTAMPTZ,
    duration INTEGER,
    court_id UUID,
    urn VARCHAR(11),
    exhibit_reference VARCHAR(15),
    defendant_name VARCHAR(100),
    witness_name VARCHAR(100),
    recording_version VARCHAR(4),
    recording_version_number VARCHAR(1),
    mp4_file_name TEXT,
    file_size_mb VARCHAR(10),

    -- Status and error tracking
    recording_id UUID,
    status VF_MIGRATION_STATUS NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    error_message TEXT,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now()
);
