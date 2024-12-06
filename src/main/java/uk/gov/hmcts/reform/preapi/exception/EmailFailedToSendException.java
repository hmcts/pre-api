package uk.gov.hmcts.reform.preapi.exception;

public class EmailFailedToSendException extends RuntimeException {
    private static final long serialVersionUID = 6579141936356533851L;

    public EmailFailedToSendException(String email) {
        super("Failed to send email to: " + email);
    }
}
