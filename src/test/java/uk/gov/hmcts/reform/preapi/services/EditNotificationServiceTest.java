package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.util.Set;

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
    private ShareBooking shareBooking1;
    private ShareBooking shareBooking2;

    private static String testEmail = "test.court@example.com";

    @BeforeEach
    void setup() {
        shareWith1 = HelperFactory.createUser("First", "User", "example1@example.com",
                                                   new Timestamp(System.currentTimeMillis()), null, null);

        shareWith2 = HelperFactory.createUser("Second", "User", "example2@example.com",
                                                   new Timestamp(System.currentTimeMillis()), null, null);

        sharedBy = HelperFactory.createUser("Court", "Clerk", "court.clerk@example.com",
                                                   new Timestamp(System.currentTimeMillis()), null, null);

        court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "TC");

        testCase = HelperFactory.createCase(court, "Test Case", false, null);

        booking = HelperFactory.createBooking(testCase, new Timestamp(System.currentTimeMillis()), null);

        shareBooking1 = HelperFactory.createShareBooking(shareWith1, sharedBy, booking,
                                                                       new Timestamp(System.currentTimeMillis()));

        shareBooking2 = HelperFactory.createShareBooking(shareWith2, sharedBy, booking,
                                                                       new Timestamp(System.currentTimeMillis()));

        booking.setShares(Set.of(shareBooking1, shareBooking2));

        when(emailServiceFactory.getEnabledEmailService()).thenReturn(emailService);
        when(emailService.recordingEdited(any(), any())).thenReturn(emailResponse);
        when(mockEditRequest.getSourceRecording()).thenReturn(mockRecording);
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
        when(mockEditRequest.getJointlyAgreed()).thenReturn(true);
        court.setGroupEmail(testEmail);
        underTest.onEditRequestSubmitted(mockEditRequest);

        verify(emailService, times(1)).editingJointlyAgreed(testEmail, mockEditRequest);
        verifyNoMoreInteractions(emailService);
    }

    @DisplayName("Should be able to notify appropriately when edit request is submitted not jointly agreed")
    @Test
    void testEditRequestSubmittedNotJointlyAgreed() {
        when(mockEditRequest.getJointlyAgreed()).thenReturn(false);
        court.setGroupEmail(testEmail);
        underTest.onEditRequestSubmitted(mockEditRequest);

        verify(emailService, times(1)).editingNotJointlyAgreed(testEmail, mockEditRequest);
        verifyNoMoreInteractions(emailService);
    }

    @DisplayName("Should be able to notify appropriately when edit request is rejected")
    @Test
    void testEditRequestRejected() {
        court.setGroupEmail(testEmail);
        underTest.onEditRequestRejected(mockEditRequest);

        verify(emailService, times(1)).editingRejected(testEmail, mockEditRequest);
        verifyNoMoreInteractions(emailService);
    }

    @DisplayName("Should not attempt to email if court email address is null")
    @Test
    void testEditRequestNotificationsWhenNoCourtEmail() {
        court.setGroupEmail(null);

        underTest.onEditRequestSubmitted(mockEditRequest);
        verifyNoInteractions(emailService);

        underTest.onEditRequestRejected(mockEditRequest);
        verifyNoInteractions(emailService);
    }

}
