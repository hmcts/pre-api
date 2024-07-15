package uk.gov.hmcts.reform.preapi.tasks;

import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

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
                        log.info("Stopping live event {}", liveEventDTO.getId());

                        var captureSession = captureSessionService.findByLiveEventId(liveEventDTO.getId());
                        var search = new SearchRecordings();
                        search.setCaptureSessionId(captureSession.getId());
                        var recordings = recordingService.findAll(search, true, Pageable.unpaged());

                        recordings.forEach(recording -> {
                            try {
                                mediaService.stopLiveEvent(captureSession, recording.getId());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        });
                    });

        log.info("Completed CleanupLiveEvents task");
    }
}
