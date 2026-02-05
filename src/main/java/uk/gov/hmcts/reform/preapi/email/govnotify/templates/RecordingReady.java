package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class RecordingReady extends BaseTemplate {
    public RecordingReady(String to,
                          String firstName,
                          String lastName,
                          String caseRef,
                          String courtName,
                          String portalLink) {
        super(
            to,
            Map.of(
                "first_name", firstName,
                "last_name", lastName,
                "case_ref", caseRef,
                "court", courtName,
                "portal_link", portalLink
            )
        );
    }

    @Override
    public String getTemplateId() {
        return "6ad8d468-4a18-4180-9c08-c6fae055a385";
    }
}
