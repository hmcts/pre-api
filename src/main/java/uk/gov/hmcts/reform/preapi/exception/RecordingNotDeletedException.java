package uk.gov.hmcts.reform.preapi.exception;

public class RecordingNotDeletedException extends RuntimeException {
    private static final long serialVersionUID = 6579841826356533851L;

    public RecordingNotDeletedException() {
        super("Cannot delete because and associated recording has not been deleted.");
    }

    public RecordingNotDeletedException(Exception exception) {
        super(exception);
    }

}
