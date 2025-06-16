package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class RecordingNotDeletedException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6579841826356533851L;

    public RecordingNotDeletedException() {
        super("Cannot delete because and associated recording has not been deleted.");
    }
}
