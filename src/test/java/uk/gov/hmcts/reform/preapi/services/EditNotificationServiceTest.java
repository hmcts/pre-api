package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.email.EmailResponse;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.util.Set;

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

    private User shareWith1;
    private User shareWith2;
    private User sharedBy;
    private Case testCase;
    private Court court;
    private Booking booking;
    private Participant witness;
    private Participant defendant;
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
        court.setGroupEmail(testEmail);

        testCase = HelperFactory.createCase(court, "Test Case", false, null);

        booking = HelperFactory.createBooking(testCase, new Timestamp(System.currentTimeMillis()), null);

        witness = new Participant();
        witness.setFirstName("Witness");
        witness.setParticipantType(ParticipantType.WITNESS);
        defendant = new Participant();
        defendant.setFirstName("Defendant lastname");
        defendant.setParticipantType(ParticipantType.DEFENDANT);
        booking.setParticipants(Set.of(witness, defendant));

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
        when(mockEditRequest.getSourceRecording()).thenReturn(mockRecording);
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);
        when(mockRecording.getCaptureSession()).thenReturn(mockCaptureSession);
        when(mockCaptureSession.getBooking()).thenReturn(booking);
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
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);
        when(mockEditRequest.getJointlyAgreed()).thenReturn(true);

        underTest.editRequestStatusWasUpdated(mockEditRequest);

        ArgumentCaptor<EditRequest> paramsCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(emailService, times(1)).sendEmailAboutEditingRequest(paramsCaptor.capture());
        verifyNoMoreInteractions(emailService);

        assertThat(paramsCaptor.getValue()).isEqualTo(mockEditRequest);
    }

    @DisplayName("Should be able to notify appropriately when edit request is submitted not jointly agreed")
    @Test
    void testEditRequestSubmittedNotJointlyAgreed() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);
        when(mockEditRequest.getJointlyAgreed()).thenReturn(false);
        underTest.editRequestStatusWasUpdated(mockEditRequest);

        ArgumentCaptor<EditRequest> paramsCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(emailService, times(1)).sendEmailAboutEditingRequest(paramsCaptor.capture());
        verifyNoMoreInteractions(emailService);

        assertThat(paramsCaptor.getValue()).isEqualTo(mockEditRequest);
    }

    @DisplayName("Should be able to notify appropriately when edit request is rejected")
    @Test
    void testEditRequestRejected() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.REJECTED);

        underTest.editRequestStatusWasUpdated(mockEditRequest);

        ArgumentCaptor<EditRequest> paramsCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(emailService, times(1)).sendEmailAboutEditingRequest(paramsCaptor.capture());
        verifyNoMoreInteractions(emailService);
        assertThat(paramsCaptor.getValue()).isEqualTo(mockEditRequest);
    }

    @DisplayName("Should not notify when edit request is approved, completed, or non-submission status")
    @Test
    void testNoEditRequestNotificationForOtherStatusTypes() {
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.COMPLETE);
        underTest.editRequestStatusWasUpdated(mockEditRequest);
        verifyNoInteractions(emailService);

        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.APPROVED);
        underTest.editRequestStatusWasUpdated(mockEditRequest);
        verifyNoInteractions(emailService);

        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.DRAFT);
        underTest.editRequestStatusWasUpdated(mockEditRequest);
        verifyNoInteractions(emailService);

        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);
        underTest.editRequestStatusWasUpdated(mockEditRequest);
        verifyNoInteractions(emailService);

        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PROCESSING);
        underTest.editRequestStatusWasUpdated(mockEditRequest);
        verifyNoInteractions(emailService);

        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.ERROR);
        underTest.editRequestStatusWasUpdated(mockEditRequest);
        verifyNoInteractions(emailService);
    }

    @DisplayName("Should pass on attempt to email as null court email address is now handled downstream")
    @Test
    void testEditRequestNotificationsWhenNoCourtEmail() {
        court.setGroupEmail(null);

        underTest.editRequestStatusWasUpdated(mockEditRequest);

        ArgumentCaptor<EditRequest> paramsCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(emailService, times(1)).sendEmailAboutEditingRequest(paramsCaptor.capture());
        verifyNoMoreInteractions(emailService);
        assertThat(paramsCaptor.getValue()).isEqualTo(mockEditRequest);
    }

}
