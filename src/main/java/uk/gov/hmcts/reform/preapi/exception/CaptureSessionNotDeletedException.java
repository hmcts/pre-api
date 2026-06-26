package uk.gov.hmcts.reform.preapi.exception;

import java.util.UUID;

public class CaptureSessionNotDeletedException extends RuntimeException {
    private static final long serialVersionUID = 6579841826356533851L;
    private static final String DEFAULT_MESSAGE = "Cannot delete because an associated recording has not been deleted.";

    public CaptureSessionNotDeletedException() {
        super(DEFAULT_MESSAGE);
    }

    public CaptureSessionNotDeletedException(UUID captureSessionId, UUID recordingId) {
        super(String.format(
            "%s captureSessionId=%s recordingId=%s",
            DEFAULT_MESSAGE,
            captureSessionId,
            recordingId
        ));
    }
}
