CREATE TABLE job_failures (
  recording_id UUID PRIMARY KEY,
  event_time TIMESTAMPTZ NOT NULL,
  error_reason VARCHAR(45) NOT NULL,
  message VARCHAR(255) NOT NULL
);
