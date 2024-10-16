package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class RecordingReady extends BaseTemplate {
    private final String templateId = "6ad8d468-4a18-4180-9c08-c6fae055a385";

    public RecordingReady(String to, String firstName, String caseRef, String courtName, String portalLink) {
        super(
            to,
            Map.of(
                "first_name", firstName,
                "case_ref", caseRef,
                "court", courtName,
                "portal_link", portalLink
            )
        );
    }
}
