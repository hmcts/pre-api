package uk.gov.hmcts.reform.preapi.tasks;

import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;

@Component
@Slf4j
public class CleanupLiveEvents implements Runnable {

    private final MediaServiceBroker mediaServiceBroker;

    @Autowired
    CleanupLiveEvents(MediaServiceBroker mediaServiceBroker) {
        this.mediaServiceBroker = mediaServiceBroker;
    }

    @Override
    public void run() {
        log.info("Running CleanupLiveEvents task");

        var mediaService = mediaServiceBroker.getEnabledMediaService();

        // Find all Live events currently running and stop and delete them along with their streaming endpoints and
        // locators
        mediaService.getLiveEvents().stream()
                    .filter(liveEventDTO -> liveEventDTO
                        .getResourceState().equals(LiveEventResourceState.RUNNING.toString())
                    ).forEach(liveEventDTO -> {
                        log.info("Stopping live event {}", liveEventDTO.getId());
                        // @todo uncomment this line when https://github.com/hmcts/pre-api/pull/579/ is merged
                        // mediaService.stopLiveEvent(liveEventDTO.getId());
                    });

        log.info("Completed CleanupLiveEvents task");
    }
}
