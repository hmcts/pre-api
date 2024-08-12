package uk.gov.hmcts.reform.preapi.exception;

public class UnprocessableContentException extends RuntimeException {
    private static final long serialVersionUID = 6579941826346533422L;

    public UnprocessableContentException(String message) {
        super("Unprocessable Content: " + message);
    }

    public UnprocessableContentException(Exception exception) {
        super(exception);
    }
}
