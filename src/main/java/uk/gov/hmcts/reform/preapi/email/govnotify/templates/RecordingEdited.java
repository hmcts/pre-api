package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

public class RecordingEdited extends RecordingReady {
    public RecordingEdited(String to,
                           String firstName,
                           String lastName,
                           String caseRef,
                           String courtName,
                           String portalLink) {
        super(to, firstName, lastName, caseRef, courtName, portalLink);
    }

    public String getTemplateId() {
        return "1da03824-84e8-425d-b913-c2bac661e64a";
    }
}
