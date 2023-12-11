package uk.gov.hmcts.reform.preapi.exception;

public class UpdateDeletedException extends RuntimeException {
    private static final long serialVersionUID = 6579941826346533853L;

    public UpdateDeletedException(String message) {
        super("Trying to undeleted: " + message);
    }

    public UpdateDeletedException(Exception exception) {
        super(exception);
    }
}
