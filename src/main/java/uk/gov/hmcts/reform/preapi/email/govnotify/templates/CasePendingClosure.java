package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class CasePendingClosure extends BaseTemplate {
    public CasePendingClosure(String to, String firstName, String lastName, String caseRef, String closureDate) {
        super(
            to,
            Map.of(
                "first_name", firstName,
                "last_name", lastName,
                "case_ref", caseRef,
                "closure_date", closureDate
            )
        );
    }

    public String getTemplateId() {
        return "5322ba5c-f4c4-4d1b-807c-16f56f0d8d0c";
    }
}
