package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessage;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessageSection;
import uk.gov.hmcts.reform.preapi.controllers.MediaServiceController;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class CheckForMissingRecordings implements Runnable {

    private final CaptureSessionService captureSessionService;
    private final SlackClient slackClient;
    private final String platformEnv;

    @Autowired
    CheckForMissingRecordings(CaptureSessionService captureSessionService,
                              SlackClient slackClient,
                              @Value("${platform-env}") String platformEnv) {
        this.captureSessionService = captureSessionService;
        this.slackClient = slackClient;
        this.platformEnv = platformEnv;
    }

    @Override
    public void run() {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        log.info("Running CheckForMissingRecordings task: looking for missing recordings from {}", yesterday.toLocalDate());

        List<String> missingBookingIds = captureSessionService.findMissingRecordingIds(Timestamp.valueOf(yesterday));

        if (!missingBookingIds.isEmpty()) {
            log.info("Found missing recordings: {}\nSending alert to Slack channel...", missingBookingIds);
            SlackMessageSection section = new SlackMessageSection("Missing Recordings", missingBookingIds,
                    "Expected to find recordings in final storage account");

            SlackMessage slackMessage = SlackMessage.builder()
                    .environment(platformEnv)
                    .sections(List.of(section))
                    .build();

            slackClient.postSlackMessage(slackMessage.toJson());
        }

        log.info("Completed CheckForMissingRecordings task");
    }
}

