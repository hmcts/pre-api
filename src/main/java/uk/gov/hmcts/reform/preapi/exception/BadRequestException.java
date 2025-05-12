package uk.gov.hmcts.reform.preapi.exception;

public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 6679241837356553851L;

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
