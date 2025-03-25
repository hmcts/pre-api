CREATE TYPE public.EDIT_REQUEST_STATUS AS ENUM (
  'PENDING',
  'PROCESSING',
  'COMPLETE',
  'ERROR'
);

CREATE TABLE edit_requests (
  id UUID PRIMARY KEY,
  source_recording_id UUID REFERENCES recordings(id) NOT NULL,
  edit_instruction JSON,
  status public.EDIT_REQUEST_STATUS NOT NULL,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_by UUID REFERENCES users(id) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  modified_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
