package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import java.util.Map;

public class EditingNotJointlyAgreed extends BaseTemplate {
    public EditingNotJointlyAgreed(String to,
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
        return "fb11d2a9-086d-4f27-9208-a3ddfe696919";
    }
}
