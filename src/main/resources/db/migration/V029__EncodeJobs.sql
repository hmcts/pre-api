CREATE TYPE public.ENCODE_TRANSFORM AS ENUM (
  'ENCODE_FROM_INGEST',
  'ENCODE_FROM_MP4'
);

CREATE TABLE encode_jobs (
  id UUID PRIMARY KEY,
  capture_session_id UUID REFERENCES capture_sessions(id) NOT NULL,
  recording_id UUID NOT NULL,
  job_name VARCHAR(255) NOT NULL,
  transform public.ENCODE_TRANSFORM NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ
);
