package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.AzureMediaService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CleanupLiveEventsTest {

    private static MediaServiceBroker mediaServiceBroker;
    private static CaptureSessionService captureSessionService;
    private static RecordingService recordingService;
    private static AzureMediaService mediaService;
    private static UserService userService;
    private static UserAuthenticationService userAuthenticationService;

    private static final String CRON_USER_EMAIL = "test@test.com";

    @BeforeEach
    void beforeEach() {
        mediaServiceBroker = mock(MediaServiceBroker.class);
        captureSessionService = mock(CaptureSessionService.class);
        recordingService = mock(RecordingService.class);
        mediaService = mock(AzureMediaService.class);
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

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @DisplayName("Test CleanupLiveEvents run method")
    @Test
    public void testRun() throws InterruptedException {

        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setId(captureSessionId.toString().replace("-", ""));
        liveEventDTO.setName(liveEventDTO.getId());
        liveEventDTO.setResourceState("Running");
        List<LiveEventDTO> liveEventDTOList = new ArrayList<>();
        liveEventDTOList.add(liveEventDTO);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvents()).thenReturn(liveEventDTOList);

        var mockCaptureSession = new CaptureSessionDTO();
        mockCaptureSession.setId(captureSessionId);
        mockCaptureSession.setStatus(RecordingStatus.RECORDING);

        var mockRecording = new RecordingDTO();
        mockRecording.setId(UUID.randomUUID());

        var mockRecording2 = new RecordingDTO();
        mockRecording2.setId(UUID.randomUUID());

        when(captureSessionService.findByLiveEventId(liveEventDTO.getName())).thenReturn(mockCaptureSession);
        when(recordingService.findAll(any(SearchRecordings.class), eq(false), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(mockRecording, mockRecording2)));

        var mockCaptureSessionProcessing = mockCaptureSession;
        mockCaptureSessionProcessing.setStatus(RecordingStatus.PROCESSING);
        when(captureSessionService.stopCaptureSession(captureSessionId,
                                                      RecordingStatus.PROCESSING,
                                                      mockRecording.getId()))
            .thenReturn(mockCaptureSessionProcessing);
        when(captureSessionService.stopCaptureSession(captureSessionId,
                                                      RecordingStatus.PROCESSING,
                                                      mockRecording2.getId()))
            .thenReturn(mockCaptureSessionProcessing);

        CleanupLiveEvents cleanupLiveEvents = new CleanupLiveEvents(mediaServiceBroker,
                                                                    captureSessionService,
                                                                    recordingService,
                                                                    userService,
                                                                    userAuthenticationService,
                                                                    CRON_USER_EMAIL);

        cleanupLiveEvents.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).getLiveEvents();
        verify(mediaService, times(1)).stopLiveEvent(mockCaptureSession, mockRecording.getId());
        verify(mediaService, times(1)).stopLiveEvent(mockCaptureSession, mockRecording2.getId());
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
                                                                    CRON_USER_EMAIL);


        cleanupLiveEvents.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).getLiveEvents();
        verify(captureSessionService, times(1))
            .stopCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, mockRecording.getId());
    }
}
