package uk.gov.hmcts.reform.preapi.email.govnotify.templates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EditRequestEmailTemplateTest {

    @Test
    @DisplayName("Should create REJECTED edit email template")
    void getRejectionEmailTemplate() {
        EditEmailParameters parameters = mock(EditEmailParameters.class);
        when(parameters.getEditRequestStatus()).thenReturn(EditRequestStatus.REJECTED);
        when(parameters.getJointlyAgreed()).thenReturn(true);
        when(parameters.getToEmailAddress()).thenReturn("test@email.com");

        Map<String, Object> testVariables = ImmutableMap.of("jointly_agreed", "Yes");
        when(parameters.getEmailParameters()).thenReturn(testVariables);

        EditRequestEmailTemplate template = new EditRequestEmailTemplate(parameters);

        assertThat(template.getTemplateId()).isEqualTo("aa2a836f-b6f0-46dc-91e0-1698822c5137");
        assertThat(template.getEditingEmailType()).isEqualTo(EditingEmailType.REJECTED);
        assertThat(template.getTo()).isEqualTo("test@email.com");
        assertThat(template.getVariables()).containsEntry("jointly_agreed", "Yes");
    }

    @Test
    @DisplayName("Should create JOINTLY_AGREED edit email template")
    void getJointlyAgreedEmailTemplate() {
        EditEmailParameters parameters = mock(EditEmailParameters.class);
        when(parameters.getEditRequestStatus()).thenReturn(EditRequestStatus.SUBMITTED);
        when(parameters.getJointlyAgreed()).thenReturn(true);
        when(parameters.getEmailParameters()).thenReturn(ImmutableMap.of("jointly_agreed", "Yes"));
        when(parameters.getToEmailAddress()).thenReturn("test@email.com");

        EditRequestEmailTemplate template = new EditRequestEmailTemplate(parameters);

        assertThat(template.getTemplateId()).isEqualTo("018ad5d2-c7ba-42a8-ad50-6baaaecf210c");
        assertThat(template.getEditingEmailType()).isEqualTo(EditingEmailType.JOINTLY_AGREED);
        assertThat(template.getTo()).isEqualTo("test@email.com");
        assertThat(template.getVariables()).containsEntry("jointly_agreed", "Yes");
    }

    @Test
    @DisplayName("Should create NOT_JOINTLY_AGREED edit email template")
    void getNotJointlyAgreedEmailTemplate() {
        EditEmailParameters parameters = mock(EditEmailParameters.class);
        when(parameters.getEditRequestStatus()).thenReturn(EditRequestStatus.SUBMITTED);
        when(parameters.getJointlyAgreed()).thenReturn(false);
        when(parameters.getEmailParameters()).thenReturn(ImmutableMap.of("jointly_agreed", "No"));
        when(parameters.getToEmailAddress()).thenReturn("test@email.com");

        EditRequestEmailTemplate template = new EditRequestEmailTemplate(parameters);

        assertThat(template.getTemplateId()).isEqualTo("fb11d2a9-086d-4f27-9208-a3ddfe696919");
        assertThat(template.getEditingEmailType()).isEqualTo(EditingEmailType.NOT_JOINTLY_AGREED);
        assertThat(template.getTo()).isEqualTo("test@email.com");
        assertThat(template.getVariables()).containsEntry("jointly_agreed", "No");
    }

    @Test
    @DisplayName("Should throw exception if unrecognised email type")
    void throwExceptionIfUnrecognisedEmailType() {
        EditEmailParameters parameters = mock(EditEmailParameters.class);
        when(parameters.getEditRequestStatus()).thenReturn(EditRequestStatus.COMPLETE);

        assertThrows(IllegalArgumentException.class, () -> new EditRequestEmailTemplate(parameters));

        var message = assertThrows(
            IllegalArgumentException.class,
            () -> new EditRequestEmailTemplate(parameters)
        ).getMessage();

        assertThat(message).isEqualTo("Could not work out which type of edit email to send: edit status "
                                          + "COMPLETE, jointly agreed false");
    }

}
