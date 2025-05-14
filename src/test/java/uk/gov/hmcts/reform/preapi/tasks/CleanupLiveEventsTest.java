package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private static RecordingService recordingService;
    private static MediaKind mediaService;
    private static UserService userService;
    private static UserAuthenticationService userAuthenticationService;

    private static final String CRON_USER_EMAIL = "test@test.com";
    private static final String CRON_PLATFORM_ENV = "Staging";

    @BeforeEach
    void beforeEach() {
        mediaServiceBroker = mock(MediaServiceBroker.class);
        captureSessionService = mock(CaptureSessionService.class);
        recordingService = mock(RecordingService.class);
        mediaService = mock(MediaKind.class);
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);

        var accessDto = mock(AccessDTO.class);
        var baseAppAccessDTO = mock(BaseAppAccessDTO.class);
        when(baseAppAccessDTO.getId()).thenReturn(UUID.randomUUID());

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(accessDto);
        when(accessDto.getAppAccess()).thenReturn(Set.of(baseAppAccessDTO));

        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.ofNullable(userAuth));
    }

    @DisplayName("Test CleanupLiveEvents with Capture Session in wrong state to encode")
    @Test
    @SuppressWarnings({"checkstyle:VariableDeclarationUsageDistance"})
    public void testCaptureSessionInUnexpectedState() {
        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setId(captureSessionId.toString().replace("-", ""));
        liveEventDTO.setName(liveEventDTO.getId());
        liveEventDTO.setResourceState("Running");
        List<LiveEventDTO> liveEventDTOList = new ArrayList<>();
        liveEventDTOList.add(liveEventDTO);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvents()).thenReturn(liveEventDTOList);

        var bookingId = UUID.randomUUID();

        var mockCaptureSession = new CaptureSessionDTO();
        mockCaptureSession.setId(captureSessionId);
        mockCaptureSession.setBookingId(bookingId);
        mockCaptureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var mockRecording = new RecordingDTO();
        mockRecording.setId(UUID.randomUUID());

        var mockRecording2 = new RecordingDTO();
        mockRecording2.setId(UUID.randomUUID());

        var mockBaseUser = new BaseUserDTO();
        mockBaseUser.setId(UUID.randomUUID());
        mockBaseUser.setFirstName("Foo");
        mockBaseUser.setEmail("foo@bar.org");

        var mockUser = new UserDTO();
        mockUser.setId(UUID.randomUUID());
        mockUser.setFirstName("Foo");
        mockUser.setEmail("foo@bar.org");

        var mockShareBooking = new ShareBookingDTO();
        mockShareBooking.setId(UUID.randomUUID());
        mockShareBooking.setSharedWithUser(mockBaseUser);

        var mockCourt = new CourtDTO();
        mockCourt.setName("Test Court");

        var mockCaseDTO = new CaseDTO();
        mockCaseDTO.setReference("123456");
        mockCaseDTO.setCourt(mockCourt);

        var mockBooking = new BookingDTO();
        mockBooking.setId(bookingId);
        mockBooking.setShares(List.of(
            mockShareBooking
        ));
        mockBooking.setCaseDTO(mockCaseDTO);

        var mockCaptureSessionProcessing = new CaptureSessionDTO();
        mockCaptureSessionProcessing.setId(captureSessionId);
        mockCaptureSessionProcessing.setBookingId(bookingId);
        mockCaptureSessionProcessing.setStatus(RecordingStatus.PROCESSING);

        var mockCaptureSessionRecordingAvailable =  new CaptureSessionDTO();
        mockCaptureSessionProcessing.setId(captureSessionId);
        mockCaptureSessionProcessing.setBookingId(bookingId);
        mockCaptureSessionProcessing.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionService.findByLiveEventId(liveEventDTO.getName()))
            .thenReturn(mockCaptureSession, mockCaptureSessionRecordingAvailable);
        when(recordingService.findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(mockRecording, mockRecording2)));

        when(captureSessionService.stopCaptureSession(captureSessionId,
                                                      RecordingStatus.PROCESSING,
                                                      mockRecording.getId()))
            .thenReturn(mockCaptureSessionProcessing);
        when(captureSessionService.stopCaptureSession(captureSessionId,
                                                      RecordingStatus.PROCESSING,
                                                      mockRecording2.getId()))
            .thenReturn(mockCaptureSessionProcessing);

        when(userService.findById(mockShareBooking.getSharedWithUser().getId())).thenReturn(mockUser);

        CleanupLiveEvents cleanupLiveEvents = new CleanupLiveEvents(mediaServiceBroker,
                                                                    captureSessionService,
                                                                    recordingService,
                                                                    userService,
                                                                    userAuthenticationService,
                                                                    CRON_USER_EMAIL,
                                                                    CRON_PLATFORM_ENV);

        cleanupLiveEvents.run();
    }

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @DisplayName("Test CleanupLiveEvents run method when InterruptedException is thrown")
    @Test
    public void runInterruptedExceptionTest() throws InterruptedException {

        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setId(captureSessionId.toString().replace("-", ""));
        liveEventDTO.setName(liveEventDTO.getId());
        liveEventDTO.setResourceState("Running");
        List<LiveEventDTO> liveEventDTOList = new ArrayList<>();
        liveEventDTOList.add(liveEventDTO);

        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setStatus(RecordingStatus.STANDBY);

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvents()).thenReturn(liveEventDTOList);
        when(captureSessionService.findByLiveEventId(any())).thenReturn(captureSession);

        var mockRecording = new RecordingDTO();
        mockRecording.setId(UUID.randomUUID());

        var mockRecording2 = new RecordingDTO();
        mockRecording2.setId(UUID.randomUUID());

        when(mediaService.stopLiveEvent(captureSession, mockRecording.getId()))
            .thenThrow(InterruptedException.class);

        when(recordingService.findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(mockRecording, mockRecording2)));

        CleanupLiveEvents cleanupLiveEvents = new CleanupLiveEvents(mediaServiceBroker,
                                                                    captureSessionService,
                                                                    recordingService,
                                                                    userService,
                                                                    userAuthenticationService,
                                                                    CRON_USER_EMAIL,
                                                                    CRON_PLATFORM_ENV);

        cleanupLiveEvents.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).getLiveEvents();
        verify(captureSessionService, times(1))
            .stopCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, mockRecording.getId());
    }

    @DisplayName("Should stop live event when capture session cannot be found (only in non-prod)")
    @Test
    void runStopLiveEventForMissingCaptureSession() {
        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setId(captureSessionId.toString().replace("-", ""));
        liveEventDTO.setName(liveEventDTO.getId());
        liveEventDTO.setResourceState("Running");
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvents()).thenReturn(List.of(liveEventDTO));

        var mockBaseUser = new BaseUserDTO();
        mockBaseUser.setId(UUID.randomUUID());
        mockBaseUser.setFirstName("Foo");
        mockBaseUser.setEmail("foo@bar.org");

        var mockUser = new UserDTO();
        mockUser.setId(UUID.randomUUID());
        mockUser.setFirstName("Foo");
        mockUser.setEmail("foo@bar.org");


        var mockCourt = new CourtDTO();
        mockCourt.setName("Test Court");

        var mockCaseDTO = new CaseDTO();
        mockCaseDTO.setReference("123456");
        mockCaseDTO.setCourt(mockCourt);

        var bookingId = UUID.randomUUID();

        var mockAsset = new AssetDTO();
        mockAsset.setName(captureSessionId.toString());
        mockAsset.setDescription(bookingId.toString());

        when(mediaService.getAsset(captureSessionId.toString())).thenReturn(mockAsset);

        doThrow(NotFoundException.class).when(captureSessionService).findByLiveEventId(liveEventDTO.getName());

        var cleanupLiveEvents = new CleanupLiveEvents(mediaServiceBroker,
                                                      captureSessionService,
                                                      recordingService,
                                                      userService,
                                                      userAuthenticationService,
                                                      CRON_USER_EMAIL,
                                                      CRON_PLATFORM_ENV);

        cleanupLiveEvents.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).getLiveEvents();
        verify(captureSessionService, times(1)).findByLiveEventId(liveEventDTO.getName());
        verify(recordingService, never()).findAll(any(), eq(false), any());
        verify(mediaService, times(1)).cleanupStoppedLiveEvent(liveEventDTO.getName());
    }

    @DisplayName("Should not stop live event when capture session cannot be found (in prod)")
    @Test
    void runStopLiveEventForMissingCaptureSessionInProduction() throws InterruptedException {
        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setId(captureSessionId.toString().replace("-", ""));
        liveEventDTO.setName(liveEventDTO.getId());
        liveEventDTO.setResourceState("Running");
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvents()).thenReturn(List.of(liveEventDTO));

        var mockBaseUser = new BaseUserDTO();
        mockBaseUser.setId(UUID.randomUUID());
        mockBaseUser.setFirstName("Foo");
        mockBaseUser.setEmail("foo@bar.org");

        var mockUser = new UserDTO();
        mockUser.setId(UUID.randomUUID());
        mockUser.setFirstName("Foo");
        mockUser.setEmail("foo@bar.org");

        var mockCourt = new CourtDTO();
        mockCourt.setName("Test Court");

        var mockCaseDTO = new CaseDTO();
        mockCaseDTO.setReference("123456");
        mockCaseDTO.setCourt(mockCourt);

        doThrow(NotFoundException.class).when(captureSessionService).findByLiveEventId(liveEventDTO.getName());

        var cleanupLiveEvents = new CleanupLiveEvents(mediaServiceBroker,
                                                      captureSessionService,
                                                      recordingService,
                                                      userService,
                                                      userAuthenticationService,
                                                      CRON_USER_EMAIL,
                                                      "Production");

        cleanupLiveEvents.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).getLiveEvents();
        verify(captureSessionService, times(1)).findByLiveEventId(liveEventDTO.getName());
        verify(recordingService, never()).findAll(any(), eq(false), any());
        verify(mediaService, never()).getAsset(any());
        verify(mediaService, never()).stopLiveEvent(any(), any());
    }

    @DisplayName("Test CleanupLiveEvents run method with GovNotify enabled")
    @Test
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    public void testRunWithGovNotifyEmails() throws InterruptedException {
        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setId(captureSessionId.toString().replace("-", ""));
        liveEventDTO.setName(liveEventDTO.getId());
        liveEventDTO.setResourceState("Running");
        List<LiveEventDTO> liveEventDTOList = new ArrayList<>();
        liveEventDTOList.add(liveEventDTO);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvents()).thenReturn(liveEventDTOList);

        var bookingId = UUID.randomUUID();

        var mockCaptureSession = new CaptureSessionDTO();
        mockCaptureSession.setId(captureSessionId);
        mockCaptureSession.setBookingId(bookingId);
        mockCaptureSession.setStatus(RecordingStatus.RECORDING);

        var mockRecording = new RecordingDTO();
        mockRecording.setId(UUID.randomUUID());

        var mockRecording2 = new RecordingDTO();
        mockRecording2.setId(UUID.randomUUID());

        var mockBaseUser = new BaseUserDTO();
        mockBaseUser.setId(UUID.randomUUID());
        mockBaseUser.setFirstName("Foo");
        mockBaseUser.setLastName("Bar");
        mockBaseUser.setEmail("foo@bar.org");

        var mockUser = new UserDTO();
        mockUser.setId(UUID.randomUUID());
        mockUser.setFirstName("Foo");
        mockUser.setLastName("Bar");
        mockUser.setEmail("foo@bar.org");

        var mockShareBooking = new ShareBookingDTO();
        mockShareBooking.setId(UUID.randomUUID());
        mockShareBooking.setSharedWithUser(mockBaseUser);

        var mockCourt = new CourtDTO();
        mockCourt.setName("Test Court");

        var mockCaseDTO = new CaseDTO();
        mockCaseDTO.setReference("123456");
        mockCaseDTO.setCourt(mockCourt);

        var mockBooking = new BookingDTO();
        mockBooking.setId(bookingId);
        mockBooking.setShares(List.of(
            mockShareBooking
        ));
        mockBooking.setCaseDTO(mockCaseDTO);

        var mockCaptureSessionProcessing = new CaptureSessionDTO();
        mockCaptureSessionProcessing.setId(captureSessionId);
        mockCaptureSessionProcessing.setBookingId(bookingId);
        mockCaptureSessionProcessing.setStatus(RecordingStatus.PROCESSING);

        var mockCaptureSessionRecordingAvailable =  new CaptureSessionDTO();
        mockCaptureSessionProcessing.setId(captureSessionId);
        mockCaptureSessionProcessing.setBookingId(bookingId);
        mockCaptureSessionProcessing.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionService.findByLiveEventId(liveEventDTO.getName()))
            .thenReturn(mockCaptureSession, mockCaptureSessionRecordingAvailable);
        when(recordingService.findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(mockRecording, mockRecording2)));

        when(captureSessionService.stopCaptureSession(captureSessionId,
                                                      RecordingStatus.PROCESSING,
                                                      mockRecording.getId()))
            .thenReturn(mockCaptureSessionProcessing);
        when(captureSessionService.stopCaptureSession(captureSessionId,
                                                      RecordingStatus.PROCESSING,
                                                      mockRecording2.getId()))
            .thenReturn(mockCaptureSessionProcessing);

        when(mediaService.stopLiveEvent(mockCaptureSessionProcessing, mockRecording.getId()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.stopLiveEvent(mockCaptureSessionProcessing, mockRecording2.getId()))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionService.stopCaptureSession(captureSessionId,
                                                      RecordingStatus.RECORDING_AVAILABLE,
                                                      mockRecording.getId()))
            .thenReturn(mockCaptureSessionProcessing);
        when(captureSessionService.stopCaptureSession(captureSessionId,
                                                      RecordingStatus.RECORDING_AVAILABLE,
                                                      mockRecording2.getId()))
            .thenReturn(mockCaptureSessionProcessing);

        when(userService.findById(mockShareBooking.getSharedWithUser().getId())).thenReturn(mockUser);

        CleanupLiveEvents cleanupLiveEvents = new CleanupLiveEvents(mediaServiceBroker,
                                                                    captureSessionService,
                                                                    recordingService,
                                                                    userService,
                                                                    userAuthenticationService,
                                                                    CRON_USER_EMAIL,
                                                                    CRON_PLATFORM_ENV);

        cleanupLiveEvents.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).getLiveEvents();

        ArgumentCaptor<CaptureSessionDTO> captureSessionCaptor = ArgumentCaptor.forClass(CaptureSessionDTO.class);
        ArgumentCaptor<CaptureSessionDTO> captureSessionCaptor2 = ArgumentCaptor.forClass(CaptureSessionDTO.class);

        verify(mediaService, times(1)).stopLiveEvent(captureSessionCaptor.capture(), eq(mockRecording.getId()));
        verify(mediaService, times(1)).stopLiveEvent(captureSessionCaptor2.capture(), eq(mockRecording2.getId()));

        Assertions.assertEquals(captureSessionId, captureSessionCaptor.getValue().getId());
        Assertions.assertEquals(captureSessionId, captureSessionCaptor2.getValue().getId());
    }
}
