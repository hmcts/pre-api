package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class CaseClosed extends BaseTemplate {
    private final String templateId = "ee5c6d3f-e934-4053-9f48-0ba082b8caf4";

    public CaseClosed(String to, String firstName, String lastName, String caseRef) {
        super(
            to,
            Map.of(
                "first_name", firstName,
                "last_name", lastName,
                "case_ref", caseRef
            )
        );
    }
}
