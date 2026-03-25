package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

public class EditingNotJointlyAgreed extends BaseTemplate {
    public EditingNotJointlyAgreed(EmailParameters emailParameters) {
        super(
            emailParameters.getToEmailAddress(),
            emailParameters.getEmailParameterMap()
        );
    }

    @Override
    public String getTemplateId() {
        return "fb11d2a9-086d-4f27-9208-a3ddfe696919";
    }
}
