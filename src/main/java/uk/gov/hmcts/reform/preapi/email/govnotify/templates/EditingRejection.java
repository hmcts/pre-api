package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class EditingRejection extends BaseTemplate {
    public EditingRejection(String to,
                            String caseReference,
                            String rejectionReason,
                            String courtName,
                            String witnessName,
                            String defendantNames,
                            String editSummary,
                            boolean jointlyAgreed,
                            String portalLink) {
        super(
            to,
            Map.of(
                "case_reference", caseReference,
                "rejection_reason", rejectionReason,
                "court_name", courtName,
                "witness_name", witnessName,
                "defendant_names", defendantNames,
                "edit_summary", editSummary,
                "jointly_agreed", jointlyAgreed ? "Yes" : "No",
                "portal_link", portalLink
            )
        );
    }

    public String getTemplateId() {
        return "aa2a836f-b6f0-46dc-91e0-1698822c5137";
    }
}
