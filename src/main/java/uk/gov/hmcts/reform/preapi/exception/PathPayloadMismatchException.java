package uk.gov.hmcts.reform.preapi.exception;

public class PathPayloadMismatchException extends RuntimeException {
    private static final long serialVersionUID = 6579941826346533851L;

    public PathPayloadMismatchException(String pathName, String modelPropertyName) {
        super("Path " + pathName + " does not match payload property " + modelPropertyName);
    }

    public PathPayloadMismatchException(Exception exception) {
        super(exception);
    }
}
