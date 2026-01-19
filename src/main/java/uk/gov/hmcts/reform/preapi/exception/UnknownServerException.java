package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class UnknownServerException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6579941826346533852L;

    public UnknownServerException(String message) {
        super("Unknown Server Exception: " + message);
    }

    public UnknownServerException(String message, Throwable cause) {
        super("Unknown Server Exception: " + message, cause);
    }

    public UnknownServerException(Exception exception) {
        super(exception);
    }
}
