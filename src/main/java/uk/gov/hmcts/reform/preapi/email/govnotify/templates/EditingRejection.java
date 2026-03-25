package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

public class EditingRejection extends BaseTemplate {
    public EditingRejection(EmailParameters emailParameters) {
        super(
            emailParameters.getToEmailAddress(),
            emailParameters.getEmailParameterMap()
        );
    }

    @Override
    public String getTemplateId() {
        return "aa2a836f-b6f0-46dc-91e0-1698822c5137";
    }
}
