package uk.gov.hmcts.reform.preapi.exception;

public class BatchTimeoutException extends RuntimeException {
    private static final long serialVersionUID = 6479841826356533851L;

    public BatchTimeoutException(int numJobsStillProcessing) {
        super("Timeout waiting for transform jobs to complete for batch, "
                  + numJobsStillProcessing
                  + " job(s) still processing");
    }
}
