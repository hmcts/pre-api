package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class EmailVerification extends BaseTemplate {
    public EmailVerification(String to, String firstName, String lastName, String verificationCode) {
        super(
            to,
            Map.of(
                "first_name", firstName,
                "last_name", lastName,
                "verification_code", verificationCode
            )
        );
    }

    public String getTemplateId() {
        return "24de8c44-06af-4489-9e83-134b48894d36";
    }
}
