package uk.gov.hmcts.reform.preapi.tasks;

import lombok.Cleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = StartLiveEvents.class)
public class StartLiveEventsTest {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private CaptureSessionService captureSessionService;

    private static final String CRON_USER_EMAIL = "test@test.com";

    private StartLiveEvents startLiveEvents;

    @BeforeEach
    void setUp() {
        startLiveEvents = new StartLiveEvents(
            userService,
            userAuthenticationService,
            captureSessionService, mediaServiceBroker, bookingService, CRON_USER_EMAIL,
            20,
            100
        );

        var accessDto = mock(AccessDTO.class);
        var baseAppAccessDTO = mock(BaseAppAccessDTO.class);
        when(baseAppAccessDTO.getId()).thenReturn(UUID.randomUUID());
        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(accessDto);
        when(accessDto.getAppAccess()).thenReturn(Set.of(baseAppAccessDTO));
        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.ofNullable(userAuth));
    }

    @Test
    @DisplayName("Should start live event for booking without a capture session")
    void runSuccess() {
        var bookingId1 = UUID.randomUUID();
        var bookingId2 = UUID.randomUUID();
        var booking1 = mock(BookingDTO.class);
        var booking2 = mock(BookingDTO.class);
        when(booking1.getId()).thenReturn(bookingId1);
        when(booking2.getId()).thenReturn(bookingId2);
        when(booking1.getCaptureSessions()).thenReturn(List.of());
        when(booking2.getCaptureSessions()).thenReturn(List.of());

        when(bookingService.findAllBookingsForToday())
            .thenReturn(List.of(booking1, booking2));

        var mockMediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mockMediaService);

        var captureSession1 = new CaptureSessionDTO();
        captureSession1.setId(UUID.randomUUID());
        captureSession1.setStatus(RecordingStatus.INITIALISING);
        var captureSession2 = new CaptureSessionDTO();
        captureSession2.setId(UUID.randomUUID());
        captureSession2.setStatus(RecordingStatus.INITIALISING);

        @Cleanup var mockedUUID = mockStatic(UUID.class);
        mockedUUID.when(UUID::randomUUID).thenReturn(captureSession1.getId(), captureSession2.getId());
        when(captureSessionService.findById(any())).thenReturn(captureSession1, captureSession2);

        var liveEvent1 = mock(LiveEventDTO.class);
        when(liveEvent1.getName()).thenReturn(MediaResourcesHelper.getSanitisedLiveEventId(captureSession1.getId()));
        when(liveEvent1.getResourceState()).thenReturn("Starting", "Running");
        when(liveEvent1.getInputRtmp()).thenReturn(null, "some rtmps");

        var liveEvent2 = mock(LiveEventDTO.class);
        when(liveEvent2.getName()).thenReturn(MediaResourcesHelper.getSanitisedLiveEventId(captureSession2.getId()));
        when(liveEvent2.getResourceState()).thenReturn("Starting", "Running");
        when(liveEvent2.getInputRtmp()).thenReturn(null, "some rtmps");

        when(mockMediaService.getLiveEvents()).thenReturn(List.of(liveEvent1, liveEvent2));

        startLiveEvents.run();

        verify(captureSessionService, times(2)).upsert(any());
        verify(mockMediaService, times(2)).startLiveEvent(any());
        verify(mockMediaService, times(3)).getLiveEvents();
    }
}
