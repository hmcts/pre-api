package uk.gov.hmcts.reform.preapi.exception;

public class B2CControllerException extends RuntimeException {
    private static final long serialVersionUID = 5579141837456533851L;

    public B2CControllerException(String message, Throwable cause) {
        super(message, cause);
    }
}
