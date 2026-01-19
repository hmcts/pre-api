package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class UnsupportedMediaTypeException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6679231437391522851L;

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
