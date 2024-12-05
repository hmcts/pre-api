package uk.gov.hmcts.reform.preapi.email;

import lombok.AllArgsConstructor;
import lombok.Getter;
import uk.gov.service.notify.SendEmailResponse;

@AllArgsConstructor
@Getter
public class EmailResponse {
    private final String body;
    private final String subject;
    private final String fromEmail;

    public static EmailResponse fromGovNotifyResponse(SendEmailResponse response) {
        return new EmailResponse(response.getBody(), response.getSubject(), response.getFromEmail().orElse(""));
    }
}
