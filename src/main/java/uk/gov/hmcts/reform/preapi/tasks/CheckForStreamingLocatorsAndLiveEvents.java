package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.StreamingLocatorDTO;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;

import java.util.List;

/**
 * Check for any streaming locators/live events and send an alert if there are any.
 */
@Component
@Slf4j
public class CheckForStreamingLocatorsAndLiveEvents implements Runnable {

    private final MediaServiceBroker mediaServiceBroker;

    @Autowired
    CheckForStreamingLocatorsAndLiveEvents(MediaServiceBroker mediaServiceBroker) {
        this.mediaServiceBroker = mediaServiceBroker;
    }

    @Override
    public void run() {
        log.info("Running CheckForStreamingLocatorsAndLiveEvents task");

        List<StreamingLocatorDTO> streamingLocatorDTOList = mediaServiceBroker.getEnabledMediaService().getStreamingLocators();
        List<LiveEventDTO> liveEventDTOList = mediaServiceBroker.getEnabledMediaService().getLiveEvents();

        //TODO:
        // Form the URLs for locators then message be like
        // The nightly task found the below still running:
        // Streaming locators:
        // . abcde-dffddg <-- UUID (link)
        // Live events:
        // abcde-efegfg <-- UUID (link)

        log.info(streamingLocatorDTOList.toString());
        log.info(liveEventDTOList.toString());

        log.info("Completed CheckForStreamingLocatorsAndLiveEvents task");
    }
}

