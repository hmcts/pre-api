package uk.gov.hmcts.reform.preapi.exception;

public class NotFoundException extends RuntimeException {

    private static final long serialVersionUID = 6579941826346533850L;

    public NotFoundException(String message) {
        super("Not found: " + message);
    }

    public NotFoundException(Exception exception) {
        super(exception);
    }
}
