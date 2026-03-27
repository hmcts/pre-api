CREATE TYPE public.REENCODE_JOB_STATUS AS ENUM (
  'PENDING',
  'PROCESSING',
  'COMPLETE',
  'ERROR'
);

ALTER TABLE public.capture_sessions
  ADD COLUMN modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE public.recordings
  ADD COLUMN modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE TABLE public.recording_reencode_jobs (
  id UUID PRIMARY KEY,
  recording_id UUID REFERENCES recordings(id) NOT NULL,
  capture_session_id UUID REFERENCES capture_sessions(id) NOT NULL,
  migration_record_id UUID REFERENCES vf_migration_records(id) NOT NULL,
  source_container VARCHAR(255) NOT NULL,
  source_blob_name VARCHAR(255) NOT NULL,
  reencoded_blob_name VARCHAR(255) NOT NULL,
  status public.REENCODE_JOB_STATUS NOT NULL,
  error_message TEXT,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recording_reencode_jobs_status_created_at
  ON public.recording_reencode_jobs (status, created_at);

CREATE INDEX idx_recording_reencode_jobs_recording_id
  ON public.recording_reencode_jobs (recording_id);
