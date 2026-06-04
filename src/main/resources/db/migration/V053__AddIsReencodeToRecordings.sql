ALTER TABLE recordings
ADD COLUMN is_reencode BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE recordings
SET is_reencode = TRUE
WHERE COALESCE(edit_instruction::jsonb #>> '{editInstructions,forceReencode}', 'false') = 'true'
   OR COALESCE(edit_instruction::jsonb ->> 'forceReencode', 'false') = 'true';

CREATE INDEX IF NOT EXISTS idx_recordings_is_reencode
ON public.recordings (is_reencode)
WHERE is_reencode = TRUE;
