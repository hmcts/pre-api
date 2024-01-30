package uk.gov.hmcts.reform.preapi.exception;

public class ImmutableDataException extends RuntimeException {

    private static final long serialVersionUID = 6579941126346533850L;

    public ImmutableDataException(String message) {
        super("Data is immutable and cannot be changed. Id: " + message);
    }

    public ImmutableDataException(Exception exception) {
        super(exception);
    }
}
