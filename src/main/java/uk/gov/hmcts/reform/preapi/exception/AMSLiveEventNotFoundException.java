package uk.gov.hmcts.reform.preapi.exception;

public class AMSLiveEventNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 6579141826356533851L;

    public AMSLiveEventNotFoundException(String id) {
        super("AMS Live event not found with id " + id);
    }
}
