package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.util.Map;

@Getter
@Setter
@Builder(toBuilder = true)
public class EmailParameters {
    private EditRequestStatus editRequestStatus;
    private String toEmailAddress; // court group email
    private String witnessName;
    private String defendantName;
    private String caseReference;
    private Integer numberOfRequestedEditInstructions;
    private String courtName;
    private String editSummary;
    private String portalURL;
    private String rejectionReason;
    private Boolean jointlyAgreed;

    public Map<String, Object> getEmailParameterMap() {
        return Map.of(
            "rejection_reason", getEditSummary(),
            "jointly_agreed", getJointlyAgreed() ? "Yes" : "No",
            "case_reference", getCaseReference(),
            "court_name", getCourtName(),
            "witness_name", getWitnessName(),
            "defendant_names", getDefendantName(),
            "edit_summary", getEditSummary(),
            "portal_link", getPortalURL(),
            "edit_count", getNumberOfRequestedEditInstructions()
        );

    }
}
