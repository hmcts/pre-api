package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.AzureMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CleanupLiveEventsTest {

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @DisplayName("Test CleanupLiveEvents run method")
    @Test
    public void testRun() throws InterruptedException {
        var mediaServiceBroker = mock(MediaServiceBroker.class);
        var captureSessionService = mock(CaptureSessionService.class);
        var recordingService = mock(RecordingService.class);
        var mediaService = mock(AzureMediaService.class);

        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setId(captureSessionId.toString().replace("-", ""));
        liveEventDTO.setResourceState("Running");
        List<LiveEventDTO> liveEventDTOList = new ArrayList<>();
        liveEventDTOList.add(liveEventDTO);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvents()).thenReturn(liveEventDTOList);

        var mockCaptureSession = mock(CaptureSessionDTO.class);
        mockCaptureSession.setId(captureSessionId);

        var mockRecording = new RecordingDTO();
        mockRecording.setId(UUID.randomUUID());

        var mockRecording2 = new RecordingDTO();
        mockRecording2.setId(UUID.randomUUID());

        when(captureSessionService.findByLiveEventId(liveEventDTO.getId())).thenReturn(mockCaptureSession);
        when(recordingService.findAll(any(SearchRecordings.class), eq(true), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(mockRecording, mockRecording2)));

        CleanupLiveEvents cleanupLiveEvents = new CleanupLiveEvents(mediaServiceBroker,
                                                                    captureSessionService,
                                                                    recordingService);

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
        var mediaServiceBroker = mock(MediaServiceBroker.class);
        var captureSessionService = mock(CaptureSessionService.class);
        var recordingService = mock(RecordingService.class);
        var mediaService = mock(AzureMediaService.class);

        var captureSessionId = UUID.randomUUID();
        var liveEventDTO = new LiveEventDTO();
        liveEventDTO.setId(captureSessionId.toString().replace("-", ""));
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

        when(recordingService.findAll(any(SearchRecordings.class), eq(true), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(mockRecording, mockRecording2)));

        CleanupLiveEvents cleanupLiveEvents = new CleanupLiveEvents(mediaServiceBroker,
                                                                    captureSessionService,
                                                                    recordingService);

        cleanupLiveEvents.run();

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).getLiveEvents();
        verify(captureSessionService, times(1))
            .stopCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, mockRecording.getId());
    }
}
