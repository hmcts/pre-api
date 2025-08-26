package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class ForbiddenException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6579941826146533854L;

    public ForbiddenException(String message) {
        super(message);
    }
}
