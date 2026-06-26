package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

public class CaseClosureCancelled extends CaseClosed {
    public CaseClosureCancelled(String to, String firstName, String lastName, String caseRef) {
        super(to, firstName, lastName, caseRef);
    }

    @Override
    public String getTemplateId() {
        return "5fba3021-a835-4f98-a575-83cc5fcb83a4";
    }
}
