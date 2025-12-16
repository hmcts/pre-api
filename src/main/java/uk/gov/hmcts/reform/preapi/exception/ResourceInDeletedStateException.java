package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class ResourceInDeletedStateException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6579941826346533851L;

    public ResourceInDeletedStateException(String className, String id) {
        super("Resource " + className + "(" + id + ") is in a deleted state and cannot be updated");
    }

    public ResourceInDeletedStateException(Exception exception) {
        super(exception);
    }
}
