package uk.gov.hmcts.reform.preapi.exception;

import java.io.Serial;

public class AMSLiveEventNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 6579141826356533851L;

    public AMSLiveEventNotFoundException(String id) {
        super("AMS Live event not found with id " + id);
    }
}
