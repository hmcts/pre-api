package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class EditingJointlyAgreed extends BaseTemplate {
    public EditingJointlyAgreed(String to,
                                String caseReference,
                                int editCount,
                                String courtName,
                                String witnessName,
                                String defendantNames,
                                String editSummary,
                                String portalLink) {
        super(
            to,
            Map.of(
                "case_reference", caseReference,
                "edit_count", editCount,
                "court_name", courtName,
                "witness_name", witnessName,
                "defendant_names", defendantNames,
                "edit_summary", editSummary,
                "portal_link", portalLink
            )
        );
    }

    @Override
    public String getTemplateId() {
        return "018ad5d2-c7ba-42a8-ad50-6baaaecf210c";
    }
}
