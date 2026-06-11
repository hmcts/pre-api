package uk.gov.hmcts.reform.preapi.email.govnotify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.email.EmailResponse;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosed;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CaseClosureCancelled;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.CasePendingClosure;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.PortalInvite;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingEdited;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.RecordingReady;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.EmailFailedToSendException;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.utils.JsonUtils;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = GovNotify.class)
@TestPropertySource(properties = {
    "email.govNotify.key=test",
    "portal.url=http://localhost:8080"
})
public class GovNotifyTest {

    @MockitoBean
    NotificationClient mockGovNotifyClient;

    private static final String govNotifyEmailResponse = """
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


    private GovNotify underTest;

    private EditRequest editRequest;

    private static final User sampleUser = getSampleUser();
    private static final Case sampleCase = getSampleCase();

    @BeforeEach
    void setUp() throws NotificationClientException {
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenReturn(new SendEmailResponse(govNotifyEmailResponse));

        underTest = new GovNotify("http://localhost:8080", mockGovNotifyClient);

        Booking booking = mock(Booking.class);
        when(booking.getCaseId()).thenReturn(sampleCase);
        when(booking.getWitnessName()).thenReturn("Witness Name");
        when(booking.getDefendantName()).thenReturn("Defendant Name");

        CaptureSession captureSession = mock(CaptureSession.class);
        when(captureSession.getBooking()).thenReturn(booking);

        Recording recording = mock(Recording.class);
        when(recording.getCaptureSession()).thenReturn(captureSession);

        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setJointlyAgreed(true);
        dto.setStatus(EditRequestStatus.REJECTED);

        EditCutInstructionDTO instruction1 = new EditCutInstructionDTO(0, 30, "first reason");
        EditInstructions editInstructions = new EditInstructions(List.of(instruction1), new ArrayList<>());

        editRequest = new EditRequest();
        editRequest.updateEditRequestFromDto(dto, recording, JsonUtils.toJson(editInstructions));
    }

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
        var template = new CasePendingClosure(
            "to",
            "firstName",
            "lastName",
            "caseRef",
            Timestamp.valueOf("2025-01-01 00:00:00.0")
        );
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

        assertThrows(
            IllegalArgumentException.class, () ->
                new EmailServiceFactory("nonexistent", true, List.of(govNotify))
        );
    }


    @DisplayName(("Should send recording ready email"))
    @Test
    void shouldSendRecordingReadyEmail() {
        var response = underTest.recordingReady(sampleUser, sampleCase);

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send recording edited email"))
    @Test
    void shouldSendRecordingEditedEmail() {
        var response = underTest.recordingEdited(sampleUser, sampleCase);

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send portal invite email"))
    @Test
    void shouldSendPortalInviteEmail() {
        var response = underTest.portalInvite(sampleUser);

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send case pending closure email"))
    @Test
    void shouldSendCasePendingClosureEmail() {
        var response = underTest.casePendingClosure(
            sampleUser, sampleCase,
            Timestamp.valueOf("2025-01-01 00:00:00.0")
        );

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send case closed email"))
    @Test
    void shouldSendCaseClosedEmail() {
        var response = underTest.caseClosed(sampleUser, sampleCase);

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should send case closure cancelled email"))
    @Test
    void shouldSendCaseClosureCancelledEmail() {
        var response = underTest.caseClosureCancelled(sampleUser, sampleCase);

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @Captor
    private ArgumentCaptor<Map<String, Object>> variablesCaptor;

    @Test
    @DisplayName("Should send editing email")
    void sendEditRejectionEmail() throws NotificationClientException {
        EmailResponse response = underTest.sendEmailAboutEditingRequest(editRequest)
            .orElseThrow(() -> new EmailFailedToSendException("Something went wrong"));

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");

        ArgumentCaptor<String> emailAddressCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> referenceCaptor = ArgumentCaptor.forClass(String.class);

        verify(mockGovNotifyClient, times(1)).sendEmail(
            templateCaptor.capture(),
            emailAddressCaptor.capture(),
            variablesCaptor.capture(),
            referenceCaptor.capture()
        );

        // Gov Notify template ID
        assertThat(templateCaptor.getValue()).isEqualTo("aa2a836f-b6f0-46dc-91e0-1698822c5137");
        assertThat(emailAddressCaptor.getValue()).isEqualTo(sampleCase.getCourt().getGroupEmail());
        assertThat(variablesCaptor.getValue().get("case_reference")).isEqualTo(sampleCase.getReference());
        assertThat(variablesCaptor.getValue().get("court_name")).isEqualTo(sampleCase.getCourt().getName());
        assertThat(variablesCaptor.getValue().get("defendant_names")).isEqualTo("Defendant Name");
        assertThat(variablesCaptor.getValue().get("edit_summary")).isNotNull();
        assertThat(variablesCaptor.getValue().get("edit_count"))
            .isEqualTo(1);
        assertThat(variablesCaptor.getValue().get("jointly_agreed")).isEqualTo("Yes");
        assertThat(variablesCaptor.getValue().get("rejection_reason")).isEqualTo("");
        assertThat(variablesCaptor.getValue().get("portal_link")).isEqualTo("http://localhost:8080");
    }

    @DisplayName(("Should fail to send recording ready email"))
    @Test
    void shouldFailToSendRecordingReadyEmail() throws NotificationClientException {

        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(
            EmailFailedToSendException.class,
            () -> underTest.recordingReady(sampleUser, sampleCase)
        ).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + sampleUser.getEmail());
    }

    @DisplayName(("Should fail to send recording edited email"))
    @Test
    void shouldFailToSendRecordingEditedEmail() throws NotificationClientException {

        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(
            EmailFailedToSendException.class,
            () -> underTest.recordingEdited(sampleUser, sampleCase)
        ).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + sampleUser.getEmail());
    }

    @DisplayName(("Should absorb error thrown by email parameters creation"))
    @Test
    void shouldAbsorbErrorThrownByEmailParameters() {
        editRequest.setSourceRecording(null); // will cause exception

        Optional<EmailResponse> emailResponse = underTest.sendEmailAboutEditingRequest(editRequest);

        assertThat(emailResponse).isEmpty();
        verifyNoInteractions(mockGovNotifyClient);
    }

    @DisplayName(("Should fail to send portal invite email"))
    @Test
    void shouldFailToSendPortalInviteEmail() throws NotificationClientException {

        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(
            EmailFailedToSendException.class,
            () -> underTest.portalInvite(sampleUser)
        ).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + sampleUser.getEmail());
    }

    @DisplayName(("Should fail to send case pending closure email"))
    @Test
    void shouldFailToSendCasePendingClosureEmail() throws NotificationClientException {

        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(
            EmailFailedToSendException.class,
            () -> underTest.casePendingClosure(
                sampleUser,
                sampleCase,
                Timestamp.valueOf("2025-01-01 00:00:00.0")
            )
        )
            .getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + sampleUser.getEmail());
    }

    @DisplayName(("Should fail to send case closed email"))
    @Test
    void shouldFailToSendCaseClosedEmail() throws NotificationClientException {

        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(
            EmailFailedToSendException.class,
            () -> underTest.caseClosed(sampleUser, sampleCase)
        ).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + sampleUser.getEmail());
    }

    @DisplayName(("Should fail to send case closure cancelled email"))
    @Test
    void shouldFailToSendCaseClosureCancelledEmail() throws NotificationClientException {

        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(
            EmailFailedToSendException.class,
            () -> underTest.caseClosureCancelled(sampleUser, sampleCase)
        ).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + sampleUser.getEmail());
    }

    @DisplayName(("Should send email verification email"))
    @Test
    void shouldSendEmailVerificationEmail() {
        var response = underTest.emailVerification(sampleUser.getEmail(), sampleUser.getFirstName(),
                                                   sampleUser.getLastName(), "123456");

        assertThat(response.getFromEmail()).isEqualTo("SENDER EMAIL");
        assertThat(response.getSubject()).isEqualTo("SUBJECT TEXT");
        assertThat(response.getBody()).isEqualTo("MESSAGE TEXT");
    }

    @DisplayName(("Should fail to send email verification email"))
    @Test
    void shouldFailToSendEmailVerificationEmail() throws NotificationClientException {
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(
            EmailFailedToSendException.class,
            () -> underTest.emailVerification(
                sampleUser.getEmail(),
                sampleUser.getFirstName(),
                sampleUser.getLastName(),
                "123456"
            )
        ).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: " + sampleUser.getEmail());
    }

    @Test
    @DisplayName("Should fail to send editing email")
    void shouldFailToSendEditingEmail() throws NotificationClientException {
        when(mockGovNotifyClient.sendEmail(any(), any(), any(), any()))
            .thenThrow(mock(NotificationClientException.class));

        var message = assertThrows(
            EmailFailedToSendException.class,
            () -> underTest.sendEmailAboutEditingRequest(editRequest)
        ).getMessage();

        assertThat(message).isEqualTo("Failed to send email to: "
                                          + sampleCase.getCourt().getGroupEmail());
    }

    @DisplayName("Should prefer alternative email when it ends with .cjsm.net")
    @Test
    void shouldPreferAlternativeEmailWithCjsmNet() throws Exception {
        var user = new User();
        user.setEmail("user@example.com");
        user.setAlternativeEmail("user@example.com.cjsm.net");

        var method = GovNotify.class.getDeclaredMethod("getUsersPreferredEmail", User.class);
        method.setAccessible(true);

        var result = (String) method.invoke(underTest, user);

        assertThat(result).isEqualTo("user@example.com.cjsm.net");
    }

    @DisplayName("Should prefer primary email when it ends with .cjsm.net and alternative doesn't")
    @Test
    void shouldPreferPrimaryEmailWithCjsmNet() throws Exception {
        var user = new User();
        user.setEmail("user@example.com.cjsm.net");
        user.setAlternativeEmail("user@example.com");

        var method = GovNotify.class.getDeclaredMethod("getUsersPreferredEmail", User.class);
        method.setAccessible(true);

        var result = (String) method.invoke(underTest, user);

        assertThat(result).isEqualTo("user@example.com.cjsm.net");
    }

    @DisplayName("Should prefer alternative email when both end with .cjsm.net")
    @Test
    void shouldPreferAlternativeEmailWhenBothHaveCjsmNet() throws Exception {
        var user = new User();
        user.setEmail("user@example1.com.cjsm.net");
        user.setAlternativeEmail("user@example2.com.cjsm.net");

        var method = GovNotify.class.getDeclaredMethod("getUsersPreferredEmail", User.class);
        method.setAccessible(true);

        var result = (String) method.invoke(underTest, user);

        assertThat(result).isEqualTo("user@example2.com.cjsm.net");
    }

    @DisplayName("Should fallback to primary email when neither ends with .cjsm.net")
    @Test
    void shouldFallbackToPrimaryEmailWhenNoCjsmNet() throws Exception {
        var user = new User();
        user.setEmail("user@example.com");
        user.setAlternativeEmail("user@other.com");

        var method = GovNotify.class.getDeclaredMethod("getUsersPreferredEmail", User.class);
        method.setAccessible(true);

        var result = (String) method.invoke(underTest, user);

        assertThat(result).isEqualTo("user@example.com");
    }

    @DisplayName("Should fallback to primary email when alternative email is null")
    @Test
    void shouldFallbackToPrimaryEmailWhenAlternativeIsNull() throws Exception {
        var user = new User();
        user.setEmail("user@example.com");
        user.setAlternativeEmail(null);

        var method = GovNotify.class.getDeclaredMethod("getUsersPreferredEmail", User.class);
        method.setAccessible(true);

        var result = (String) method.invoke(underTest, user);

        assertThat(result).isEqualTo("user@example.com");
    }

    @DisplayName("Should prefer primary email when it ends with .cjsm.net and alternative is null")
    @Test
    void shouldPreferPrimaryEmailWhenAlternativeIsNullAndPrimaryHasCjsmNet() throws Exception {
        var user = new User();
        user.setEmail("user@example.com.cjsm.net");
        user.setAlternativeEmail(null);

        var method = GovNotify.class.getDeclaredMethod("getUsersPreferredEmail", User.class);
        method.setAccessible(true);

        var result = (String) method.invoke(underTest, user);

        assertThat(result).isEqualTo("user@example.com.cjsm.net");
    }

    private static User getSampleUser() {
        var user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("johndoe@example.com");
        return user;
    }

    private static Case getSampleCase() {
        var forCase = new Case();
        forCase.setReference("123456");
        var court = new Court();
        court.setName("Court Name");
        court.setGroupEmail("group-email@example.com");
        forCase.setCourt(court);
        return forCase;
    }

}
