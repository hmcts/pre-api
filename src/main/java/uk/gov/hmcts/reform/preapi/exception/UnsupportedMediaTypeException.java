package uk.gov.hmcts.reform.preapi.exception;

public class UnsupportedMediaTypeException extends RuntimeException {

    private static final long serialVersionUID = 6679231437391522851L;

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
