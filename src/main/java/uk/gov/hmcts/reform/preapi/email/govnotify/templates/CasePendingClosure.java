package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;

public class CasePendingClosure extends BaseTemplate {
    public CasePendingClosure(String to, String firstName, String lastName, String caseRef, Timestamp closureDate) {
        super(
            to,
            Map.of(
                "first_name", firstName,
                "last_name", lastName,
                "case_ref", caseRef,
                "closure_date", new SimpleDateFormat("d MMMM yyyy", Locale.UK).format(closureDate)
            )
        );
    }

    public String getTemplateId() {
        return "5322ba5c-f4c4-4d1b-807c-16f56f0d8d0c";
    }
}
