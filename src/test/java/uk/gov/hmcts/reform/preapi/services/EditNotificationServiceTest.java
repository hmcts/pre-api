package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
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
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO.toDTO;

@SpringBootTest(classes = EditNotificationService.class)
class EditNotificationServiceTest {

    @MockitoBean
    private EmailServiceFactory emailServiceFactory;

    @MockitoBean
    private RecordingRepository recordingRepository;

    @Autowired
    private EditNotificationService underTest;

    @MockitoBean
    private IEmailService emailService;

    @MockitoBean
    private EditRequestDTO mockEditRequestDto;

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

    private Participant witnessParticipant;
    private Participant defendantParticipant;

    private User shareWith1;
    private User shareWith2;
    private Case testCase;
    private Court court;
    private Booking booking;

    private static final String testEmail = "test.court@example.com";

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

        User sharedBy = HelperFactory.createUser(
            "Court", "Clerk", "court.clerk@example.com",
            new Timestamp(System.currentTimeMillis()), null, null
        );

        court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "TC", testEmail);

        testCase = HelperFactory.createCase(court, "Test Case", false, null);

        witnessParticipant = HelperFactory.createParticipant(
            testCase,
            ParticipantType.WITNESS,
            "Witness first name",
            "Witness last name",
            null
        );
        defendantParticipant = HelperFactory.createParticipant(
            testCase,
            ParticipantType.DEFENDANT,
            "Defendant first name",
            "Defendant last name",
            null
        );

        booking = HelperFactory.createBooking(
            testCase, new Timestamp(System.currentTimeMillis()), null,
            Set.of(this.witnessParticipant, this.defendantParticipant)
        );

        ShareBooking shareBooking1 = HelperFactory.createShareBooking(
            shareWith1, sharedBy, booking,
            new Timestamp(System.currentTimeMillis())
        );

        ShareBooking shareBooking2 = HelperFactory.createShareBooking(
            shareWith2, sharedBy, booking,
            new Timestamp(System.currentTimeMillis())
        );

        booking.setShares(Set.of(shareBooking1, shareBooking2));

        when(emailServiceFactory.getEnabledEmailService()).thenReturn(emailService);
        when(emailService.recordingEdited(any(), any())).thenReturn(emailResponse);

        // Recording fields
        UUID recordingId = UUID.randomUUID();
        when(mockRecording.getId()).thenReturn(recordingId);
        when(mockRecording.getCaptureSession()).thenReturn(mockCaptureSession);
        when(mockRecording.getEditRequest()).thenReturn(mockEditRequest);

        // Capture session fields
        when(mockCaptureSession.getBooking()).thenReturn(booking);

        // Edit request fields
        final List<EditCutInstructions> editInstructions = List.of(
            new EditCutInstructions(UUID.randomUUID(), 0, 30, "first thirty seconds reason"),
            new EditCutInstructions(UUID.randomUUID(), 45, 50, "first thirty seconds reason"),
            new EditCutInstructions(UUID.randomUUID(), 61, 120, "")
        );

        when(mockEditRequestDto.getSourceRecordingId()).thenReturn(recordingId);
        when(mockEditRequestDto.getEditCutInstructions()).thenReturn(toDTO(editInstructions));
        when(mockEditRequestDto.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);

        when(mockEditRequest.getSourceRecordingId()).thenReturn(recordingId);
        when(mockEditRequest.getEditCutInstructions()).thenReturn(editInstructions);
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);

        when(recordingRepository.findById(recordingId)).thenReturn(Optional.of(mockRecording));
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
        when(mockEditRequestDto.getJointlyAgreed()).thenReturn(true);
        when(mockEditRequestDto.getStatus()).thenReturn(EditRequestStatus.SUBMITTED);

        underTest.editRequestStatusWasUpdated(mockEditRequestDto);

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
            .isEqualTo(mockEditRequestDto.getEditCutInstructions().size());
        assertThat(emailParameters.getCourtName()).isEqualTo(booking.getCaseId().getCourt().getName());
        assertThat(emailParameters.getEditSummary()).isEqualTo("""
                                                                   Edit 1:\s
                                                                   Start time: 00:00
                                                                   End time: 00:00:30
                                                                   Time Removed: 00:00:30
                                                                   Reason: first thirty seconds reason

                                                                   Edit 2:\s
                                                                   Start time: 00:00:45
                                                                   End time: 00:00:50
                                                                   Time Removed: 00:00:05
                                                                   Reason: first thirty seconds reason

                                                                   Edit 3:\s
                                                                   Start time: 00:01:01
                                                                   End time: 00:02
                                                                   Time Removed: 00:00:59
                                                                   Reason:\s

                                                                   """);
        assertThat(emailParameters.getRejectionReason()).isEqualTo(null);
        assertThat(emailParameters.getJointlyAgreed()).isEqualTo(true);
    }

    @DisplayName("Should be able to notify appropriately when edit request is submitted not jointly agreed")
    @Test
    void testEditRequestSubmittedNotJointlyAgreed() {
        when(mockEditRequestDto.getJointlyAgreed()).thenReturn(false);

        underTest.editRequestStatusWasUpdated(mockEditRequestDto);

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
            .isEqualTo(mockEditRequestDto.getEditCutInstructions().size());
        assertThat(emailParameters.getCourtName()).isEqualTo(booking.getCaseId().getCourt().getName());
        assertThat(emailParameters.getEditSummary()).isEqualTo("""
                                                                   Edit 1:\s
                                                                   Start time: 00:00
                                                                   End time: 00:00:30
                                                                   Time Removed: 00:00:30
                                                                   Reason: first thirty seconds reason

                                                                   Edit 2:\s
                                                                   Start time: 00:00:45
                                                                   End time: 00:00:50
                                                                   Time Removed: 00:00:05
                                                                   Reason: first thirty seconds reason

                                                                   Edit 3:\s
                                                                   Start time: 00:01:01
                                                                   End time: 00:02
                                                                   Time Removed: 00:00:59
                                                                   Reason:\s

                                                                   """);
        assertThat(emailParameters.getRejectionReason()).isEqualTo(null);
    }

    @DisplayName("Should be able to notify appropriately when edit request is rejected")
    @Test
    void testEditRequestRejected() {
        when(mockEditRequestDto.getStatus()).thenReturn(EditRequestStatus.REJECTED);
        when(mockEditRequestDto.getRejectionReason()).thenReturn("rejected reason");

        underTest.editRequestStatusWasUpdated(mockEditRequestDto);

        ArgumentCaptor<EditEmailParameters> captor = ArgumentCaptor.forClass(EditEmailParameters.class);
        verify(emailService, times(1)).sendEmailAboutEditingRequest(captor.capture());
        verifyNoMoreInteractions(emailService);

        EditEmailParameters emailParameters = captor.getValue();

        assertThat(emailParameters.getEditRequestStatus()).isEqualTo(EditRequestStatus.REJECTED);
        assertThat(emailParameters.getToEmailAddress()).isEqualTo(testEmail);
        assertThat(emailParameters.getWitnessName()).isEqualTo(witnessParticipant.getFirstName());
        assertThat(emailParameters.getDefendantName())
            .isEqualTo(format("%s %s", defendantParticipant.getFirstName(), defendantParticipant.getLastName()));
        assertThat(emailParameters.getCaseReference()).isEqualTo(booking.getCaseId().getReference());
        assertThat(emailParameters.getNumberOfRequestedEditInstructions())
            .isEqualTo(mockEditRequestDto.getEditCutInstructions().size());
        assertThat(emailParameters.getCourtName()).isEqualTo(booking.getCaseId().getCourt().getName());
        assertThat(emailParameters.getEditSummary()).isEqualTo("""
                                                                   Edit 1:\s
                                                                   Start time: 00:00
                                                                   End time: 00:00:30
                                                                   Time Removed: 00:00:30
                                                                   Reason: first thirty seconds reason

                                                                   Edit 2:\s
                                                                   Start time: 00:00:45
                                                                   End time: 00:00:50
                                                                   Time Removed: 00:00:05
                                                                   Reason: first thirty seconds reason

                                                                   Edit 3:\s
                                                                   Start time: 00:01:01
                                                                   End time: 00:02
                                                                   Time Removed: 00:00:59
                                                                   Reason:\s

                                                                   """);
        assertThat(emailParameters.getRejectionReason()).isEqualTo("rejected reason");
        assertThat(emailParameters.getJointlyAgreed()).isEqualTo(false);
    }

    @DisplayName("Pass on attempt to email if court email address is null")
    @Test
    void testEditRequestNotificationsWhenNoCourtEmail() {
        court.setGroupEmail(null);

        underTest.editRequestStatusWasUpdated(mockEditRequestDto); // Exception handled downstream

        ArgumentCaptor<EditEmailParameters> captor = ArgumentCaptor.forClass(EditEmailParameters.class);
        verify(emailService, times(1)).sendEmailAboutEditingRequest(captor.capture());
        verifyNoMoreInteractions(emailService);
        EditEmailParameters emailParameters = captor.getValue();

        assertThat(emailParameters.getToEmailAddress()).isEqualTo(null);
    }

}
