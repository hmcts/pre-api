package uk.gov.hmcts.reform.preapi.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosed;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosureCancelled;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CasePendingClosure;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.PortalInvite;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingEdited;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingReady;
import uk.gov.service.notify.SendEmailResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GovNotifyTest {

    @DisplayName("Should create CaseClosed template")
    @Test
    void shouldCreateCaseClosedTemplate() {
        var template = new CaseClosed("to", "firstName", "lastName", "caseRef");
        assertThat(template.getTemplateId()).isEqualTo("ee5c6d3f-e934-4053-9f48-0ba082b8caf4");
    }

    @DisplayName("Should create RecordingEdited template")
    @Test
    void shouldCreateRecordingEditedTemplate() {
        var template = new RecordingEdited("to", "firstName", "caseRef", "courtName", "portalLink");
        assertThat(template.getTemplateId()).isEqualTo("1da03824-84e8-425d-b913-c2bac661e64a");
    }

    @DisplayName("Should create CaseClosureCancelled template")
    @Test
    void shouldCreateCaseClosureCancelledTemplate() {
        var template = new CaseClosureCancelled("to", "firstName", "lastName", "caseRef");
        assertThat(template.getTemplateId()).isEqualTo("5fba3021-a835-4f98-a575-83cc5fcb83a4");
    }

    @DisplayName("Should create RecordingReady template")
    @Test
    void shouldCreateRecordingReadyTemplate() {
        var template = new RecordingReady("to", "firstName", "caseRef", "courtName", "portalLink");
        assertThat(template.getTemplateId()).isEqualTo("6ad8d468-4a18-4180-9c08-c6fae055a385");
    }

    @DisplayName("Should create CasePendingClosure template")
    @Test
    void shouldCreateCasePendingClosureTemplate() {
        var template = new CasePendingClosure("to", "firstName", "lastName", "caseRef", "closureDate");
        assertThat(template.getTemplateId()).isEqualTo("5322ba5c-f4c4-4d1b-807c-16f56f0d8d0c");
    }

    @DisplayName("Should create PortalInvite template")
    @Test
    void shouldCreatePortalInviteTemplate() {
        var template = new PortalInvite("to", "firstName", "lastName", "caseRef", "courtName", "portalLink");
        assertThat(template.getTemplateId()).isEqualTo("e04adfb8-58e0-44be-ab42-bd6d896ccfb7");
    }

    @DisplayName("Should create EmailResponse from GovNotify response")
    @Test
    void shouldCreateEmailResponseFromGovNotifyResponse() {
        var response = new SendEmailResponse(
                                            """
            {
              "id": "740e5834-3a29-46b4-9a6f-16142fde533a",
              "reference": "STRING",
              "content": {
                "subject": "SUBJECT TEXT",
                "body": "MESSAGE TEXT",
                "from_email": "SENDER EMAIL"
              },
              "uri": "https://api.notifications.service.gov.uk/v2/notifications/740e5834-3a29-46b4-9a6f-16142fde533a",
              "template": {
                "id": "f33517ff-2a88-4f6e-b855-c550268ce08a",
                "version": 1,
                "uri": "https://api.notifications.service.gov.uk/v2/template/f33517ff-2a88-4f6e-b855-c550268ce08a"
              }
            }""");
        var emailResponse = EmailResponse.fromGovNotifyResponse(response);
        assertThat(emailResponse.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(emailResponse.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(emailResponse.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Email service broker"))
    @Test
    void shouldCreateEmailServiceBroker() {
        var govNotify = new GovNotify("govnotify", "url");
        var emailServiceBroker = new EmailServiceBroker("govnotify", true, govNotify);
        assertThat(emailServiceBroker).isNotNull();
        assertThat(emailServiceBroker.getEnabledEmailService()).isEqualTo(govNotify);
        assertThat(emailServiceBroker.enable).isTrue();

        assertThat(emailServiceBroker.getEnabledEmailService(null)).isEqualTo(govNotify);
        assertThat(emailServiceBroker.getEnabledEmailService("govnotify")).isEqualTo(govNotify);
        assertThrows(IllegalArgumentException.class, () -> emailServiceBroker.getEnabledEmailService("nonexistent"));

        assertThrows(IllegalArgumentException.class, () -> new EmailServiceBroker("nonexistent", true, govNotify));
    }
}
