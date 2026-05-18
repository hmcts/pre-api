ALTER TABLE recordings
ADD COLUMN hidden_by_reencode BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE recordings
SET hidden_by_reencode = TRUE
WHERE COALESCE(edit_instruction::jsonb #>> '{editInstructions,forceReencode}', 'false') = 'true'
   OR COALESCE(edit_instruction::jsonb ->> 'forceReencode', 'false') = 'true';

CREATE INDEX IF NOT EXISTS idx_recordings_hidden_by_reencode
ON public.recordings (hidden_by_reencode)
WHERE hidden_by_reencode = TRUE;
