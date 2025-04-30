package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.email.StopLiveEventNotifierFlowClient;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CleanupLiveEventsTest {
    private static MediaServiceBroker mediaServiceBroker;
    private static CaptureSessionService captureSessionService;
    private static BookingService bookingService;
    private static MediaKind mediaService;
    private static UserService userService;
    private static UserAuthenticationService userAuthenticationService;
    private static StopLiveEventNotifierFlowClient stopLiveEventNotifierFlowClient;

    private static final String CRON_USER_EMAIL = "test@test.com";
    private static final String CRON_PLATFORM_ENV = "Staging";
    private static final int BATCH_SIZE = 3;
    private static final int BATCH_COOLDOWN = 100;
    private static final int POLLING_INTERVAL = 100;

    @BeforeEach
    void beforeEach() {
        mediaServiceBroker = mock(MediaServiceBroker.class);
        captureSessionService = mock(CaptureSessionService.class);
        mediaService = mock(MediaKind.class);
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);
        bookingService = mock(BookingService.class);
        stopLiveEventNotifierFlowClient = mock(StopLiveEventNotifierFlowClient.class);

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);

        var accessDto = mock(AccessDTO.class);
        var baseAppAccessDTO = mock(BaseAppAccessDTO.class);
        when(baseAppAccessDTO.getId()).thenReturn(UUID.randomUUID());

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(accessDto);
        when(accessDto.getAppAccess()).thenReturn(Set.of(baseAppAccessDTO));

        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.ofNullable(userAuth));
    }

    @Test
    @DisplayName("CleanupLiveEvents when no live events are running")
    public void runNoLiveEvents() {
        var cleanupLiveEvents = createCleanupLiveEventsTask();
        when(mediaService.getLiveEvents()).thenReturn(List.of());

        assertDoesNotThrow(cleanupLiveEvents::run);

        verify(mediaService, times(1)).getLiveEvents();
    }

    @Test
    @DisplayName("Should clean up live events that are capture sessions in non-production environments")
    public void runMissingCaptureSessionInNonProd() {
        var liveEvent = new LiveEventDTO();
        liveEvent.setName("something");
        liveEvent.setResourceState("Running");

        when(mediaService.getLiveEvents()).thenReturn(List.of(liveEvent));
        doThrow(NotFoundException.class).when(captureSessionService).findByLiveEventId(liveEvent.getName());

        var cleanupLiveEvents = createCleanupLiveEventsTask();

        assertDoesNotThrow(cleanupLiveEvents::run);

        verify(mediaService, times(1)).getLiveEvents();
        verify(captureSessionService, times(4)).findByLiveEventId(liveEvent.getName());
        verify(mediaService, times(2)).cleanupStoppedLiveEvent(liveEvent.getName());
    }

    @Test
    @DisplayName("Should not clean up live events that are capture sessions in production environments")
    public void runMissingCaptureSessionInProd() {
        var liveEvent = new LiveEventDTO();
        liveEvent.setName("something");
        liveEvent.setResourceState("Running");

        when(mediaService.getLiveEvents()).thenReturn(List.of(liveEvent));
        doThrow(NotFoundException.class).when(captureSessionService).findByLiveEventId(liveEvent.getName());

        var cleanupLiveEvents = new CleanupLiveEvents(
            mediaServiceBroker,
            captureSessionService,
            bookingService,
            userService,
            userAuthenticationService,
            stopLiveEventNotifierFlowClient,
            CRON_USER_EMAIL,
            "Production",
            BATCH_SIZE,
            BATCH_COOLDOWN,
            POLLING_INTERVAL
        );

        assertDoesNotThrow(cleanupLiveEvents::run);

        verify(mediaService, times(1)).getLiveEvents();
        verify(captureSessionService, times(4)).findByLiveEventId(liveEvent.getName());
        verify(mediaService, never()).cleanupStoppedLiveEvent(liveEvent.getName());
    }

    @Test
    void shouldProcessLiveEventAndTriggerNotifications() {
        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setName(captureSessionId.toString().replace("-", ""));
        liveEventDTO.setResourceState("Running");

        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setBookingId(UUID.randomUUID());

        when(captureSessionService.findByLiveEventId(liveEventDTO.getName())).thenReturn(captureSession);
        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);
        when(captureSessionService.stopCaptureSession(captureSessionId, RecordingStatus.PROCESSING, null))
            .thenReturn(captureSession);

        var court = new CourtDTO();
        court.setName("Test Court");
        var aCase = new CaseDTO();
        aCase.setReference("123456");
        aCase.setCourt(court);
        var booking = new BookingDTO();
        booking.setId(captureSession.getBookingId());
        booking.setCaseDTO(aCase);

        var user = new UserDTO();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        when(userService.findById(user.getId())).thenReturn(user);
        var share = new ShareBookingDTO();
        share.setSharedWithUser(user);
        booking.setShares(List.of(share));

        when(bookingService.findById(booking.getId())).thenReturn(booking);

        when(mediaService.getLiveEvents()).thenReturn(List.of(liveEventDTO));
        when(captureSessionService.findByLiveEventId(liveEventDTO.getName())).thenReturn(captureSession);
        when(mediaService.triggerProcessingStep1(any(), any(), any())).thenReturn("job1");
        when(mediaService.hasJobCompleted(any(), eq("job1"))).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.triggerProcessingStep2(any())).thenReturn("job2");
        when(mediaService.hasJobCompleted(any(), eq("job2"))).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(any())).thenReturn(RecordingStatus.RECORDING_AVAILABLE);

        var cleanupLiveEvents = createCleanupLiveEventsTask();

        assertDoesNotThrow(cleanupLiveEvents::run);

        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.PROCESSING), any());
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.RECORDING_AVAILABLE), any());
        verify(stopLiveEventNotifierFlowClient, times(1)).emailAfterStoppingLiveEvents(any());
    }

    @Test
    void shouldHandleNoFileInIngestStorage() {
        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setName(captureSessionId.toString().replace("-", ""));
        liveEventDTO.setResourceState("Running");

        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setBookingId(UUID.randomUUID());

        when(mediaService.getLiveEvents()).thenReturn(List.of(liveEventDTO));
        when(captureSessionService.findByLiveEventId(liveEventDTO.getName())).thenReturn(captureSession);
        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);
        when(captureSessionService.stopCaptureSession(captureSessionId, RecordingStatus.PROCESSING, null))
            .thenReturn(captureSession);
        when(mediaService.triggerProcessingStep1(any(), any(), any())).thenReturn(null);

        var cleanupLiveEvents = createCleanupLiveEventsTask();

        assertDoesNotThrow(cleanupLiveEvents::run);

        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.PROCESSING), any());
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.NO_RECORDING), any());
    }

    private CleanupLiveEvents createCleanupLiveEventsTask() {
        return new CleanupLiveEvents(
            mediaServiceBroker,
            captureSessionService,
            bookingService,
            userService,
            userAuthenticationService,
            stopLiveEventNotifierFlowClient,
            CRON_USER_EMAIL,
            CRON_PLATFORM_ENV,
            BATCH_SIZE,
            BATCH_COOLDOWN,
            POLLING_INTERVAL
        );
    }
}
