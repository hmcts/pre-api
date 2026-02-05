package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class BadRequestException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6679241837356553851L;

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
