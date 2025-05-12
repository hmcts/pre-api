package uk.gov.hmcts.reform.preapi.exception;

public class ConflictException extends RuntimeException {

    private static final long serialVersionUID = 6579941826346533854L;

    public ConflictException(String message) {
        super("Conflict: " + message);
    }

    public ConflictException(String message, Throwable cause) {
        super("Conflict: " + message, cause);
    }

    public ConflictException(Exception exception) {
        super(exception);
    }
}
