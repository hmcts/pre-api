package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = StartLiveEvents.class)
public class StartLiveEventsTest {

    @MockBean
    private UserService userService;

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    @MockBean
    private MediaServiceBroker mediaServiceBroker;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private CaptureSessionService captureSessionService;

    private static final String CRON_USER_EMAIL = "test@test.com";

    private StartLiveEvents startLiveEvents;

    @BeforeEach
    void setUp() {
        startLiveEvents = new StartLiveEvents(
            userService,
            userAuthenticationService,
            CRON_USER_EMAIL,
            mediaServiceBroker,
            bookingService,
            captureSessionService
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
        var captureSession = createCaptureSession();
        var mockMediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mockMediaService);
        when(bookingService.searchBy(
            isNull(),
            isNull(),
            isNull(),
            any(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any()
        )).thenReturn(new PageImpl<>(List.of(
            createBooking(List.of())
        )));
        when(captureSessionService.findById(any(UUID.class))).thenReturn(captureSession);
        var liveEvent1 = new LiveEventDTO();
        liveEvent1.setResourceState("Starting");
        var liveEvent2 = new LiveEventDTO();
        liveEvent2.setResourceState("Running");
        liveEvent2.setInputRtmp("ingest");

        when(mockMediaService.getLiveEvent(anyString())).thenReturn(null, liveEvent1, liveEvent2);

        startLiveEvents.run();

        verify(bookingService, times(1)).searchBy(
            isNull(),
            isNull(),
            isNull(),
            any(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any()
        );
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(mockMediaService, times(1)).startLiveEvent(captureSession);
        verify(captureSessionService, never())
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.FAILURE), isNull());
        verify(captureSessionService, times(1))
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.INITIALISING), isNull());
        verify(captureSessionService, times(1))
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.STANDBY), anyString());
        verify(mockMediaService, times(3)).getLiveEvent(anyString());
    }

    @Test
    @DisplayName("Should not start live event for booking with a capture session")
    void runSuccessAlreadyHasCaptureSession() {
        var captureSession = createCaptureSession();
        var mockMediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mockMediaService);
        when(bookingService.searchBy(
            isNull(),
            isNull(),
            isNull(),
            any(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any()
        )).thenReturn(new PageImpl<>(List.of(
            createBooking(List.of(captureSession))
        )));
        when(captureSessionService.findById(any(UUID.class))).thenReturn(captureSession);

        startLiveEvents.run();

        verify(bookingService, times(1)).searchBy(
            isNull(),
            isNull(),
            isNull(),
            any(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any()
        );
        verify(captureSessionService, never()).upsert(any(CreateCaptureSessionDTO.class));
        verify(mockMediaService, never()).startLiveEvent(captureSession);
        verify(captureSessionService, never())
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.FAILURE), isNull());
        verify(captureSessionService, never())
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.INITIALISING), isNull());
        verify(captureSessionService, never())
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.STANDBY), isNull());
    }

    @Test
    @DisplayName("Should ignore bookings where creating a capture session causes an error")
    void runSuccessErrorCreatingCaptureSession() {
        var captureSession = createCaptureSession();
        var mockMediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mockMediaService);
        when(bookingService.searchBy(
            isNull(),
            isNull(),
            isNull(),
            any(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any()
        )).thenReturn(new PageImpl<>(List.of(
            createBooking(List.of())
        )));
        when(captureSessionService.findById(any(UUID.class))).thenReturn(captureSession);

        doThrow(new NotFoundException("")).when(captureSessionService).upsert(any(CreateCaptureSessionDTO.class));

        startLiveEvents.run();

        verify(bookingService, times(1)).searchBy(
            isNull(),
            isNull(),
            isNull(),
            any(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any()
        );
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(mockMediaService, never()).startLiveEvent(captureSession);
        verify(captureSessionService, never())
            .startCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, null);
        verify(captureSessionService, never())
            .startCaptureSession(captureSession.getId(), RecordingStatus.INITIALISING, null);
        verify(captureSessionService, never())
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.STANDBY), isNull());
    }

    @Test
    @DisplayName("Should not error on failure to start live event")
    void runSuccessOnStartLiveEventFailure() {
        var captureSession = createCaptureSession();
        var mockMediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mockMediaService);
        doThrow(new NotFoundException("")).when(mockMediaService).startLiveEvent(captureSession);
        when(bookingService.searchBy(
            isNull(),
            isNull(),
            isNull(),
            any(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any()
        )).thenReturn(new PageImpl<>(List.of(
            createBooking(List.of())
        )));
        when(captureSessionService.findById(any(UUID.class))).thenReturn(captureSession);

        startLiveEvents.run();

        verify(bookingService, times(1)).searchBy(
            isNull(),
            isNull(),
            isNull(),
            any(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any()
        );
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(mockMediaService, times(1)).startLiveEvent(captureSession);
        verify(captureSessionService, times(1))
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.FAILURE), isNull());
        verify(captureSessionService, never())
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.INITIALISING), isNull());
        verify(captureSessionService, never())
            .startCaptureSession(any(UUID.class), eq(RecordingStatus.STANDBY), isNull());
    }

    private BookingDTO createBooking(List<CaptureSessionDTO> captureSessions) {
        var booking = new BookingDTO();
        booking.setId(UUID.randomUUID());
        booking.setCaptureSessions(captureSessions);
        return booking;
    }

    private CaptureSessionDTO createCaptureSession() {
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        return captureSession;
    }
}
