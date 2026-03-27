package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.email.EmailResponse;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.email.govnotify.templates.EditEmailParameters;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditNotificationService.class)
class EditNotificationServiceTest {

    @MockitoBean
    private EmailServiceFactory emailServiceFactory;

    @Autowired
    private EditNotificationService underTest;

    @MockitoBean
    private IEmailService emailService;

    @MockitoBean
    private EditRequest mockEditRequest;

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private CaptureSession mockCaptureSession;

    @MockitoBean
    private EmailResponse emailResponse;

    @MockitoBean
    private LoggingService loggingService;

    @Mock
    private Participant witnessParticipant;
    @Mock
    private Participant defendantParticipant;

    private User shareWith1;
    private User shareWith2;
    private User sharedBy;
    private Case testCase;
    private Court court;
    private Booking booking;
    private ShareBooking shareBooking1;
    private ShareBooking shareBooking2;

    private static String testEmail = "test.court@example.com";

    @BeforeEach
    void setup() {
        shareWith1 = HelperFactory.createUser(
            "First", "User", "example1@example.com",
            new Timestamp(System.currentTimeMillis()), null, null
        );

        shareWith2 = HelperFactory.createUser(
            "Second", "User", "example2@example.com",
            new Timestamp(System.currentTimeMillis()), null, null
        );

        sharedBy = HelperFactory.createUser(
            "Court", "Clerk", "court.clerk@example.com",
            new Timestamp(System.currentTimeMillis()), null, null
        );

        court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "TC");

        testCase = HelperFactory.createCase(court, "Test Case", false, null);

        when(witnessParticipant.getFirstName()).thenReturn("Witness first name");
        when(witnessParticipant.getLastName()).thenReturn("Witness last name");
        when(defendantParticipant.getFirstName()).thenReturn("Defendant first name");
        when(defendantParticipant.getLastName()).thenReturn("Defendant last name");

        booking = HelperFactory.createBooking(
            testCase, new Timestamp(System.currentTimeMillis()), null,
            Set.of(witnessParticipant, defendantParticipant)
        );

        shareBooking1 = HelperFactory.createShareBooking(
            shareWith1, sharedBy, booking,
            new Timestamp(System.currentTimeMillis())
        );

        shareBooking2 = HelperFactory.createShareBooking(
            shareWith2, sharedBy, booking,
            new Timestamp(System.currentTimeMillis())
        );

        booking.setShares(Set.of(shareBooking1, shareBooking2));

        when(emailServiceFactory.getEnabledEmailService()).thenReturn(emailService);
        when(emailService.recordingEdited(any(), any())).thenReturn(emailResponse);
        when(mockRecording.getCaptureSession()).thenReturn(mockCaptureSession);
        when(mockCaptureSession.getBooking()).thenReturn(booking);

        final List<EditCutInstructions> editInstructions = List.of(
            new EditCutInstructions(UUID.randomUUID(), 0, 30, "first thirty seconds reason"),
            new EditCutInstructions(UUID.randomUUID(), 45, 50, "first thirty seconds reason"),
            new EditCutInstructions(UUID.randomUUID(), 61, 120, "")
        );

        when(mockEditRequest.getSourceRecordingId()).thenReturn(mockRecording.getId());
        when(mockEditRequest.getEditCutInstructions()).thenReturn(editInstructions);
    }

    @DisplayName("Should be able to send notifications")
    @Test
    void testSendNotifications() {
        underTest.sendNotifications(booking);

        verify(emailService, times(1)).recordingEdited(shareWith1, testCase);
        verify(emailService, times(1)).recordingEdited(shareWith2, testCase);
        verifyNoMoreInteractions(emailService);
    }

    @DisplayName("Should be able to notify appropriately when edit request is submitted jointly agreed")
    @Test
    void testEditRequestSubmittedJointlyAgreed() {
        when(mockEditRequest.getJointlyAgreed()).thenReturn(true);
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);

        underTest.editRequestStatusWasUpdated(mockEditRequest);

        ArgumentCaptor<EditEmailParameters> captor = ArgumentCaptor.forClass(EditEmailParameters.class);
        verify(emailService, times(1)).sendEmailAboutEditingRequest(captor.capture());
        verifyNoMoreInteractions(emailService);

        EditEmailParameters emailParameters = captor.getValue();

        assertThat(emailParameters.getEditRequestStatus()).isEqualTo(EditRequestStatus.SUBMITTED);
        assertThat(emailParameters.getToEmailAddress()).isEqualTo(testEmail);
        assertThat(emailParameters.getWitnessName()).isEqualTo(witnessParticipant.getFirstName());
        assertThat(emailParameters.getDefendantName())
            .isEqualTo(format("%s %s", defendantParticipant.getFirstName(), defendantParticipant.getLastName()));
        assertThat(emailParameters.getCaseReference()).isEqualTo(booking.getCaseId().getReference());
        assertThat(emailParameters.getNumberOfRequestedEditInstructions())
            .isEqualTo(mockEditRequest.getEditCutInstructions().size());
        assertThat(emailParameters.getCourtName()).isEqualTo(booking.getCaseId().getCourt().getName());
        assertThat(emailParameters.getEditSummary()).isEqualTo("TODO");
        assertThat(emailParameters.getRejectionReason()).isEqualTo(null);
        assertThat(emailParameters.getJointlyAgreed()).isEqualTo(true);
    }

    @DisplayName("Should be able to notify appropriately when edit request is submitted not jointly agreed")
    @Test
    void testEditRequestSubmittedNotJointlyAgreed() {
        when(mockEditRequest.getJointlyAgreed()).thenReturn(true);
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);

        underTest.editRequestStatusWasUpdated(mockEditRequest);

        ArgumentCaptor<EditEmailParameters> captor = ArgumentCaptor.forClass(EditEmailParameters.class);
        verify(emailService, times(1)).sendEmailAboutEditingRequest(captor.capture());
        verifyNoMoreInteractions(emailService);

        EditEmailParameters emailParameters = captor.getValue();
        assertThat(emailParameters.getJointlyAgreed()).isEqualTo(false);

        assertThat(emailParameters.getEditRequestStatus()).isEqualTo(EditRequestStatus.SUBMITTED);
        assertThat(emailParameters.getToEmailAddress()).isEqualTo(testEmail);
        assertThat(emailParameters.getWitnessName()).isEqualTo(witnessParticipant.getFirstName());
        assertThat(emailParameters.getDefendantName())
            .isEqualTo(format("%s %s", defendantParticipant.getFirstName(), defendantParticipant.getLastName()));
        assertThat(emailParameters.getCaseReference()).isEqualTo(booking.getCaseId().getReference());
        assertThat(emailParameters.getNumberOfRequestedEditInstructions())
            .isEqualTo(mockEditRequest.getEditCutInstructions().size());
        assertThat(emailParameters.getCourtName()).isEqualTo(booking.getCaseId().getCourt().getName());
        assertThat(emailParameters.getEditSummary()).isEqualTo("TODO");
        assertThat(emailParameters.getRejectionReason()).isEqualTo(null);
    }

    @DisplayName("Should be able to notify appropriately when edit request is rejected")
    @Test
    void testEditRequestRejected() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.REJECTED);

        underTest.editRequestStatusWasUpdated(mockEditRequest);

        ArgumentCaptor<EditEmailParameters> captor = ArgumentCaptor.forClass(EditEmailParameters.class);
        verify(emailService, times(1)).sendEmailAboutEditingRequest(captor.capture());
        verifyNoMoreInteractions(emailService);

        EditEmailParameters emailParameters = captor.getValue();

        assertThat(emailParameters.getEditRequestStatus()).isEqualTo(EditRequestStatus.SUBMITTED);
        assertThat(emailParameters.getToEmailAddress()).isEqualTo(testEmail);
        assertThat(emailParameters.getWitnessName()).isEqualTo(witnessParticipant.getFirstName());
        assertThat(emailParameters.getDefendantName())
            .isEqualTo(format("%s %s", defendantParticipant.getFirstName(), defendantParticipant.getLastName()));
        assertThat(emailParameters.getCaseReference()).isEqualTo(booking.getCaseId().getReference());
        assertThat(emailParameters.getNumberOfRequestedEditInstructions())
            .isEqualTo(mockEditRequest.getEditCutInstructions().size());
        assertThat(emailParameters.getCourtName()).isEqualTo(booking.getCaseId().getCourt().getName());
        assertThat(emailParameters.getEditSummary()).isEqualTo("TODO");
        assertThat(emailParameters.getRejectionReason()).isEqualTo("I didn't like it");
        assertThat(emailParameters.getJointlyAgreed()).isEqualTo(false);
    }

    @DisplayName("Should not attempt to email if court email address is null")
    @Test
    void testEditRequestNotificationsWhenNoCourtEmail() {
        court.setGroupEmail(null);

        underTest.editRequestStatusWasUpdated(mockEditRequest);
        verifyNoInteractions(emailService);
    }

}
