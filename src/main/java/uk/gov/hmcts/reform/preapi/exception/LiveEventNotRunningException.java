package uk.gov.hmcts.reform.preapi.exception;

public class LiveEventNotRunningException extends RuntimeException {
    private static final long serialVersionUID = 6379141826356533851L;

    public LiveEventNotRunningException(String id) {
        super("Live event found but not in 'Running' state with id " + id);
    }
}
