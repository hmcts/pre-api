package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.StreamingLocatorDTO;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;

import java.util.List;

/**
 * Check for any streaming locators and send an alert if there are any.
 */
@Component
@Slf4j
public class CheckForStreamingLocators implements Runnable {

    private final MediaServiceBroker mediaServiceBroker;

    @Autowired
    CheckForStreamingLocators(MediaServiceBroker mediaServiceBroker) {
        this.mediaServiceBroker = mediaServiceBroker;
    }

    @Override
    public void run() {
        log.info("Running CheckForStreamingLocators task");

        // Get streaming locators
        List<StreamingLocatorDTO> streamingLocatorDTOList = mediaServiceBroker.getEnabledMediaService().getStreamingLocators();

        List<LiveEventDTO> liveEventDTOList = mediaServiceBroker.getEnabledMediaService().getLiveEvents();


        log.info(streamingLocatorDTOList.toString());
        log.info(liveEventDTOList.toString());

        log.info("Completed CheckForStreamingLocators task");
    }
}

