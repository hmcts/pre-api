package uk.gov.hmcts.reform.preapi.exception;

public class ConflictException extends RuntimeException {
    private static final long serialVersionUID = 6579941826346533852L;

    public ConflictException(String id) {
        super("Put Conflict: " + id + " already exists");
    }

    public ConflictException(Exception exception) {
        super(exception);
    }
}
