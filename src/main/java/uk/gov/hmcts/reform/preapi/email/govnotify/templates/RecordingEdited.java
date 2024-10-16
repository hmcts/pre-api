package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

public class RecordingEdited extends RecordingReady {
    private final String templateId = "1da03824-84e8-425d-b913-c2bac661e64a";

    public RecordingEdited(String to, String firstName, String caseRef, String courtName, String portalLink) {
        super(to, firstName, caseRef, courtName, portalLink);
    }
}
