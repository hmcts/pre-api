package uk.gov.hmcts.reform.preapi.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosed;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosureCancelled;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CasePendingClosure;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.PortalInvite;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingEdited;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingReady;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.exception.EmailFailedToSendException;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = GovNotify.class)
@TestPropertySource(properties = {
    "email.govNotify.key=test",
    "portal.url=http://localhost:8080"
})
public class GovNotifyTest {

    @MockitoBean
    NotificationClient mockGovNotifyClient;

    private final String govNotifyEmailResponse = """
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
        }""";

    @DisplayName("Should create CaseClosed template")
    @Test
    void shouldCreateCaseClosedTemplate() {
        var template = new CaseClosed("to", "firstName", "lastName", "caseRef");
        assertThat(template.getTemplateId()).isEqualTo("ee5c6d3f-e934-4053-9f48-0ba082b8caf4");
    }

    @DisplayName("Should create RecordingEdited template")
    @Test
    void shouldCreateRecordingEditedTemplate() {
        var template = new RecordingEdited("to", "firstName", "lastName", "caseRef", "courtName", "portalLink");
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
        var template = new RecordingReady("to", "firstName", "lastName", "caseRef", "courtName", "portalLink");
        assertThat(template.getTemplateId()).isEqualTo("6ad8d468-4a18-4180-9c08-c6fae055a385");
    }

    @DisplayName("Should create CasePendingClosure template")
    @Test
    void shouldCreateCasePendingClosureTemplate() {
        var template = new CasePendingClosure("to",
                                              "firstName",
                                              "lastName",
                                              "caseRef",
                                              Timestamp.valueOf("2025-01-01 00:00:00.0"));
        assertThat(template.getTemplateId()).isEqualTo("5322ba5c-f4c4-4d1b-807c-16f56f0d8d0c");
    }

    @DisplayName("Should create PortalInvite template")
    @Test
    void shouldCreatePortalInviteTemplate() {
        var template = new PortalInvite(
            "to",
            "firstName",
            "lastName",
            "portalUrl",
            "guideLink",
            "processGuideLink",
            "faqsLink",
            "editingRequestForm"
        );
        assertThat(template.getTemplateId()).isEqualTo("e04adfb8-58e0-44be-ab42-bd6d896ccfb7");
    }

    @DisplayName("Should create EmailResponse from GovNotify response")
    @Test
    void shouldCreateEmailResponseFromGovNotifyResponse() {
        var response = new SendEmailResponse(govNotifyEmailResponse);
        var emailResponse = EmailResponse.fromGovNotifyResponse(response);
        assertThat(emailResponse.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(emailResponse.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(emailResponse.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Email service factory"))
    @Test
    void shouldCreateEmailServiceFactory() {
        var govNotify = new GovNotify("GovNotify", mockGovNotifyClient);
        var emailServiceFactory = new EmailServiceFactory("GovNotify", true, List.of(govNotify));
        assertThat(emailServiceFactory).isNotNull();
        assertThat(emailServiceFactory.getEnabledEmailService()).isEqualTo(govNotify);
        assertThat(emailServiceFactory.isEnabled()).isTrue();

        assertThat(emailServiceFactory.getEnabledEmailService("GovNotify")).isEqualTo(govNotify);
        assertThrows(IllegalArgumentException.class, () -> emailServiceFactory.getEnabledEmailService("nonexistent"));

        assertThrows(IllegalArgumentException.class, () ->
            new EmailServiceFactory("nonexistent", true, List.of(govNotify))
        );
    }

    User getUser() {
        var user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("johndoe@example.com");
        return user;
    }

    Case getCase() {
        var forCase = new Case();
        forCase.setReference("123456");
        var court = new Court();
        court.setName("Court Name");
        forCase.setCourt(court);
        return forCase;
    }

    @DisplayName(("Should send recording ready email"))
    @Test
    void shouldSendRecordingReadyEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenReturn(new SendEmailResponse(govNotifyEmailResponse));

        var response = govNotify.recordingReady(getUser(), getCase());

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send recording edited email"))
    @Test
    void shouldSendRecordingEditedEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenReturn(new SendEmailResponse(govNotifyEmailResponse));

        var response = govNotify.recordingEdited(getUser(), getCase());

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send portal invite email"))
    @Test
    void shouldSendPortalInviteEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenReturn(new SendEmailResponse(govNotifyEmailResponse));

        var response = govNotify.portalInvite(getUser());

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send case pending closure email"))
    @Test
    void shouldSendCasePendingClosureEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenReturn(new SendEmailResponse(govNotifyEmailResponse));

        var response = govNotify.casePendingClosure(getUser(), getCase(), Timestamp.valueOf("2025-01-01 00:00:00.0"));

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send case closed email"))
    @Test
    void shouldSendCaseClosedEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenReturn(new SendEmailResponse(govNotifyEmailResponse));

        var response = govNotify.caseClosed(getUser(), getCase());

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send case closure cancelled email"))
    @Test
    void shouldSendCaseClosureCancelledEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenReturn(new SendEmailResponse(govNotifyEmailResponse));

        var response = govNotify.caseClosureCancelled(getUser(), getCase());

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should fail to send recording ready email"))
    @Test
    void shouldFailToSendRecordingReadyEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(EmailFailedToSendException.class,
                                   () -> govNotify.recordingReady(getUser(), getCase())).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + getUser().getEmail());
    }

    @DisplayName(("Should fail to send recording edited email"))
    @Test
    void shouldFailToSendRecordingEditedEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(EmailFailedToSendException.class,
                                   () -> govNotify.recordingEdited(getUser(), getCase())).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + getUser().getEmail());
    }

    @DisplayName(("Should fail to send portal invite email"))
    @Test
    void shouldFailToSendPortalInviteEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(EmailFailedToSendException.class,
                                   () -> govNotify.portalInvite(getUser())).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + getUser().getEmail());
    }

    @DisplayName(("Should fail to send case pending closure email"))
    @Test
    void shouldFailToSendCasePendingClosureEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(EmailFailedToSendException.class,
                                   () -> govNotify.casePendingClosure(getUser(),
                                                                      getCase(),
                                                                      Timestamp.valueOf("2025-01-01 00:00:00.0")))
            .getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + getUser().getEmail());
    }

    @DisplayName(("Should fail to send case closed email"))
    @Test
    void shouldFailToSendCaseClosedEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(EmailFailedToSendException.class,
                                   () -> govNotify.caseClosed(getUser(), getCase())).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + getUser().getEmail());
    }

    @DisplayName(("Should fail to send case closure cancelled email"))
    @Test
    void shouldFailToSendCaseClosureCancelledEmail() throws NotificationClientException {
        var govNotify = new GovNotify("http://localhost:8080", mockGovNotifyClient);
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(EmailFailedToSendException.class,
                                   () -> govNotify.caseClosureCancelled(getUser(), getCase())).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + getUser().getEmail());
    }
}
