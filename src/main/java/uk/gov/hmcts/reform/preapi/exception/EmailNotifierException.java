package uk.gov.hmcts.reform.preapi.exception;

public class EmailNotifierException extends RuntimeException {
    private static final long serialVersionUID = 6578841834246533854L;

    public EmailNotifierException(String message) {
        super(message);
    }
}
