package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessage;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessageSection;
import uk.gov.hmcts.reform.preapi.controllers.MediaServiceController;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class CheckForMissingRecordings extends RobotUserTask {

    private final CaptureSessionService captureSessionService;
    private final SlackClient slackClient;
    private final String platformEnv;

    @Autowired
    CheckForMissingRecordings(CaptureSessionService captureSessionService,
                              SlackClient slackClient,
                              UserService userService,
                              UserAuthenticationService userAuthenticationService,
                              @Value("${cron-user-email}") String cronUserEmail,
                              @Value("${platform-env}") String platformEnv) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.captureSessionService = captureSessionService;
        this.slackClient = slackClient;
        this.platformEnv = platformEnv;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Signing in robot user with email {} on env {}", cronUserEmail, platformEnv);
        signInRobotUser();

//        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate yesterday = LocalDate.of(2025, 02, 03);
        log.info("Running CheckForMissingRecordings task: looking for missing recordings from {}", yesterday);

        List<String> missingBookingIds = captureSessionService.findMissingRecordingIds(yesterday);

        if (!missingBookingIds.isEmpty()) {
            log.info("Found missing recordings: {}\nSending alert to Slack channel...", missingBookingIds);
            SlackMessageSection section = new SlackMessageSection(
                "Missing Recordings", missingBookingIds,
                "Expected to find recordings in final storage account"
            );

            SlackMessage slackMessage = SlackMessage.builder()
                .environment(platformEnv)
                .sections(List.of(section))
                .build();

            slackClient.postSlackMessage(slackMessage.toJson());
        }

        log.info("Completed CheckForMissingRecordings task");
    }
}

