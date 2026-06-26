package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class CommandExecutionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CommandExecutionException(String message) {
        super(message);
    }

    public CommandExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
