package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

public class EditingJointlyAgreed extends BaseTemplate {
    public EditingJointlyAgreed(EmailParameters emailParameters) {
        super(
            emailParameters.getToEmailAddress(),
            emailParameters.getEmailParameterMap()
        );
    }

    @Override
    public String getTemplateId() {
        return "018ad5d2-c7ba-42a8-ad50-6baaaecf210c";
    }
}
