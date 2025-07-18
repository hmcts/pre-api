package uk.gov.hmcts.reform.preapi.exception;

public class CaptureSessionNotDeletedException extends RuntimeException {
    private static final long serialVersionUID = 6579841826356533851L;

    public CaptureSessionNotDeletedException() {
        super("Cannot delete because an associated recording has not been deleted.");
    }
}
