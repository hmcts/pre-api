package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;

@Component
@Slf4j
public class CleanupStreamingLocators implements Runnable {

    private final MediaServiceBroker mediaServiceBroker;

    @Autowired
    CleanupStreamingLocators(MediaServiceBroker mediaServiceBroker) {
        this.mediaServiceBroker = mediaServiceBroker;
    }

    @Override
    public void run() {
        log.info("Running CleanupStreamingLocators task");

        mediaServiceBroker.getEnabledMediaService().deleteAllStreamingLocators();

        log.info("Completed CleanupStreamingLocators task");
    }
}

