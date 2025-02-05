package uk.gov.hmcts.reform.preapi.entities.listeners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RecordingListener.class)
public class RecordingListenerTest {

    @MockBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockBean
    private EmailServiceFactory emailServiceFactory;

    @MockBean
    private ShareBookingService shareBookingService;

    @Autowired
    private RecordingListener recordingListener;

    @Test
    @DisplayName("Should not query blob storage for duration when duration already set")
    void setDurationBeforePersistDurationCurrentlyNotNull() {
        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        recordingListener.setDurationBeforePersist(recording);

        assertThat(recording.getDuration()).isEqualTo(Duration.ofMinutes(3));

        verify(azureFinalStorageService, never()).getRecordingDuration(any());
    }

    @Test
    @DisplayName("Should query blob storage for duration when duration is null")
    void setDurationBeforePersistDurationCurrentlyNull() {
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        when(azureFinalStorageService.getRecordingDuration(recording.getId())).thenReturn(Duration.ofMinutes(3));

        recordingListener.setDurationBeforePersist(recording);

        assertThat(recording.getDuration()).isEqualTo(Duration.ofMinutes(3));

        verify(azureFinalStorageService, times(1)).getRecordingDuration(recording.getId());
    }

    @DisplayName("On Recording Created Email Service Disabled")
    @Test
    void onRecordingCreatedEmailServiceDisabled() {
        var recording = new Recording();
        var captureSession = mock(CaptureSession.class);
        recording.setCaptureSession(captureSession);

        when(captureSession.getBooking()).thenReturn(mock(Booking.class));

        when(
            shareBookingService.getSharesForCase(any(Case.class))
        ).thenReturn(Collections.emptySet());
        when(emailServiceFactory.isEnabled()).thenReturn(false);

        recordingListener.onRecordingCreated(recording);

        verify(emailServiceFactory, times(0)).getEnabledEmailService();
    }

    @DisplayName("On Recording Created Email Service Enabled No Email as no recording")
    @Test
    void onRecordingCreatedEmailServiceEnabledNoEmailNoRecording() {
        var captureSession = mock(CaptureSession.class);
        var booking = mock(Booking.class);
        var caseEntity = mock(Case.class);
        when(caseEntity.getId()).thenReturn(UUID.randomUUID());
        when(booking.getCaseId()).thenReturn(caseEntity);
        when(captureSession.getBooking()).thenReturn(booking);
        var recording = new Recording();
        recording.setCaptureSession(captureSession);

        when(emailServiceFactory.isEnabled()).thenReturn(true);
        var govNotify = mock(GovNotify.class);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(govNotify);

        recordingListener.onRecordingCreated(recording);

        verify(emailServiceFactory, times(1)).getEnabledEmailService();
        verify(govNotify, times(0)).recordingReady(any(User.class), eq(caseEntity));
    }

    @DisplayName("On Recording Created Email Service Enabled")
    @Test
    void onRecordingCreatedEmailServiceEnabled() {
        var booking = mock(Booking.class);
        var caseEntity = mock(Case.class);
        var share = createShare();
        share.setBooking(booking);
        when(caseEntity.getId()).thenReturn(UUID.randomUUID());
        when(booking.getCaseId()).thenReturn(caseEntity);
        when(booking.getShares()).thenReturn(Set.of(share));
        var captureSession = mock(CaptureSession.class);
        when(booking.getCaptureSessions()).thenReturn(Set.of(captureSession));
        when(captureSession.getBooking()).thenReturn(booking);
        when(captureSession.getStatus()).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        var recording = new Recording();
        recording.setCaptureSession(captureSession);

        when(emailServiceFactory.isEnabled()).thenReturn(true);
        var govNotify = mock(GovNotify.class);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(govNotify);

        recordingListener.onRecordingCreated(recording);

        verify(emailServiceFactory, times(1)).getEnabledEmailService();
        verify(govNotify, times(1)).recordingReady(any(User.class), eq(caseEntity));
    }

    private ShareBooking createShare() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName(user.getId().toString());
        user.setLastName(user.getId().toString());
        user.setEmail(user.getId() + "@example.com");

        var share = new ShareBooking();
        share.setId(UUID.randomUUID());
        share.setSharedWith(user);
        return share;
    }
}
