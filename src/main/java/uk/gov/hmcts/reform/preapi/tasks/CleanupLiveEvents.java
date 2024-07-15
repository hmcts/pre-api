package uk.gov.hmcts.reform.preapi.tasks;

import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.UUID;

@Component
@Slf4j
public class CleanupLiveEvents implements Runnable {

    private final MediaServiceBroker mediaServiceBroker;

    private final CaptureSessionService captureSessionService;

    private final RecordingService recordingService;

    @Autowired
    CleanupLiveEvents(MediaServiceBroker mediaServiceBroker,
                      CaptureSessionService captureSessionService,
                      RecordingService recordingService) {
        this.mediaServiceBroker = mediaServiceBroker;
        this.captureSessionService = captureSessionService;
        this.recordingService = recordingService;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Running CleanupLiveEvents task");

        var mediaService = mediaServiceBroker.getEnabledMediaService();

        // Find all Live events currently running and stop and delete them along with their streaming endpoints and
        // locators
        mediaService.getLiveEvents().stream()
                    .filter(liveEventDTO -> liveEventDTO
                        .getResourceState().equals(LiveEventResourceState.RUNNING.toString())
                    ).forEach(liveEventDTO -> {

                        log.info("Finding capture session by live event id {}", liveEventDTO.getId());
                        var captureSession = captureSessionService.findByLiveEventId(liveEventDTO.getId());
                        var search = new SearchRecordings();
                        log.info("Finding recordings by capture session {}", captureSession.getId());
                        search.setCaptureSessionId(captureSession.getId());
                        var recordings = recordingService.findAll(search, true, Pageable.unpaged());

                        if (recordings.isEmpty()) {
                            log.info("No recordings found for capture session {}", captureSession.getId());
                            stopLiveEvent(mediaService, captureSession, UUID.randomUUID(), liveEventDTO.getId());
                        } else {
                            recordings.forEach(recording -> {
                                log.info("{} recordings found for capture session {}",
                                         recordings.getSize(),
                                         captureSession.getId());
                                stopLiveEvent(mediaService, captureSession, recording.getId(), liveEventDTO.getId());
                            });
                        }
                    });

        log.info("Completed CleanupLiveEvents task");
    }

    private void stopLiveEvent(IMediaService mediaService,
                               CaptureSessionDTO captureSession,
                               UUID recordingId,
                               String liveEventId) {
        try {
            // This shouldn't happen but as this is the cleanup cron we do want to ensure the Live Events in AMS/MK
            // are terminated and cleaned up.
            // A manual process will be needed to investigate why the CaptureSession is in an unexpected state.
            if (captureSession.getStatus() != RecordingStatus.STANDBY
                && captureSession.getStatus() != RecordingStatus.RECORDING
                && captureSession.getStatus() != RecordingStatus.PROCESSING) {
                log.info(
                    "CaptureSession {} is in an unexpected state: {}",
                    captureSession.getId(),
                    captureSession.getStatus()
                );
                mediaService.cleanupStoppedLiveEvent(liveEventId);
            }
            captureSessionService.stopCaptureSession(captureSession.getId(), RecordingStatus.PROCESSING, recordingId);
            log.info("Stopping live event {}", liveEventId);
            var status = mediaService.stopLiveEvent(captureSession, recordingId);
            captureSessionService.stopCaptureSession(captureSession.getId(), status, recordingId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            captureSessionService.stopCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, recordingId);
            log.error("Failed to stop live event for capture session {}", captureSession.getId(), e);
        } catch (Exception e) {
            captureSessionService.stopCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, recordingId);
            log.error("Failed to stop live event for capture session {}", captureSession.getId(), e);
        }
    }
}
