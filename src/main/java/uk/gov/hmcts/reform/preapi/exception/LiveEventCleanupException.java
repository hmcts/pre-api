package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class LiveEventCleanupException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public LiveEventCleanupException(String message, Exception cause) {
        super(message, cause);
    }
}
