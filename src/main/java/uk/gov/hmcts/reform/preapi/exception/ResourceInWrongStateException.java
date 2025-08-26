package uk.gov.hmcts.reform.preapi.exception;

import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.io.Serial;
import java.util.UUID;

public class ResourceInWrongStateException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6579941126346533851L;

    public ResourceInWrongStateException(String className, String id, String currentState, String expectedState) {
        super("Resource " + className + "(" + id + ") is in a " + currentState + " state. "
                  + "Expected state is " + expectedState + ".");
    }

    public ResourceInWrongStateException(String className, UUID id, CaseState currentState, String expectedState) {
        super("Resource " + className + "(" + id + ") is associated with a case in the state " + currentState
                  + ". Must be in state " + expectedState + ".");
    }

    public ResourceInWrongStateException(String message) {
        super(message);
    }

    public ResourceInWrongStateException(Exception exception) {
        super(exception);
    }
}
