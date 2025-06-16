package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class NotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6579941826346533850L;

    public NotFoundException(String message) {
        super("Not found: " + message);
    }

    public NotFoundException(String message, Throwable cause) {
        super("Not found: " + message, cause);
    }

    public NotFoundException(Exception exception) {
        super(exception);
    }
}
