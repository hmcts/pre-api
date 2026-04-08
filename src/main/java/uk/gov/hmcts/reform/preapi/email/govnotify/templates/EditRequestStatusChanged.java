package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import lombok.Getter;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

public class EditRequestStatusChanged extends BaseTemplate {

    @Getter
    private final EditingEmailType editingEmailType;

    public EditRequestStatusChanged(EditEmailParameters editEmailParameters, String portalUrl) {
        super(
            editEmailParameters.getToEmailAddress(),
            editEmailParameters.getEmailParameterMap(portalUrl)
        );

        this.editingEmailType = calculateEditingEmailType(editEmailParameters);
    }

    @Override
    public String getTemplateId() {
        return switch (editingEmailType) {
            case REJECTED -> "aa2a836f-b6f0-46dc-91e0-1698822c5137";
            case NOT_JOINTLY_AGREED -> "fb11d2a9-086d-4f27-9208-a3ddfe696919";
            case JOINTLY_AGREED -> "018ad5d2-c7ba-42a8-ad50-6baaaecf210c";
        };
    }

    private EditingEmailType calculateEditingEmailType(EditEmailParameters editEmailParameters) {
        if(editEmailParameters.getEditRequestStatus() == EditRequestStatus.REJECTED) {
            return EditingEmailType.REJECTED;
        }

        if (editEmailParameters.getEditRequestStatus() == EditRequestStatus.SUBMITTED) {
            if (Boolean.TRUE.equals(editEmailParameters.getJointlyAgreed())) {
                return EditingEmailType.JOINTLY_AGREED;
            } else {
                return EditingEmailType.NOT_JOINTLY_AGREED;
            }
        }
        throw new IllegalArgumentException("Could not work out which type of edit email to send:" +
                                               " edit status %s, jointly agreed %b"
                                               + editEmailParameters.getEditRequestStatus());
    }
}
