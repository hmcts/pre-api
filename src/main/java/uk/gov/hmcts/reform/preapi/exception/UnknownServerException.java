package uk.gov.hmcts.reform.preapi.exception;

public class UnknownServerException extends RuntimeException {

    private static final long serialVersionUID = 6579941826346533852L;

    public UnknownServerException(String message) {
        super("Unknown Server Exception: " + message);
    }

    public UnknownServerException(Exception exception) {
        super(exception);
    }
}
