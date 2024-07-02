package uk.gov.hmcts.reform.preapi.exception;

public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 6579941826146533854L;

    public ForbiddenException(String message) {
        super(message);
    }
}
