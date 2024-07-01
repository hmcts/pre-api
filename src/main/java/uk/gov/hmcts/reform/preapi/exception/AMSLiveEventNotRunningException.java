package uk.gov.hmcts.reform.preapi.exception;

public class AMSLiveEventNotRunningException extends RuntimeException {
    private static final long serialVersionUID = 6379141826356533851L;

    public AMSLiveEventNotRunningException(String id) {
        super("AMS Live event found but not in 'Running' state with id " + id);
    }
}
