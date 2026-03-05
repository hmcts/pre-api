CREATE TABLE edit_cut_instructions
(
  id                 UUID PRIMARY KEY,
  edit_request_id    UUID references edit_requests(id) NOT NULL,
  start_edit_seconds INTEGER                            not null,
  end_edit_seconds   INTEGER                            NOT NULL,
  reason             VARCHAR(600) NOT NULL
);

ALTER TABLE public.edit_requests ADD COLUMN output_recording_id uuid references recordings(id);
