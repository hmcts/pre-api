package uk.gov.hmcts.reform.preapi.exception;

public class MediaKindException extends RuntimeException {
    public static final long serialVersionUID = 6579941826399933854L;

    public MediaKindException(String message) {
        super(message);
    }

    public MediaKindException() {
        super("Unable to connect to Media Service");
    }
}
