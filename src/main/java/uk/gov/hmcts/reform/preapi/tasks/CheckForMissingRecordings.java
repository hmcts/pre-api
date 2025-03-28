package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessage;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessageSection;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Finds capture sessions from yesterday.
 * Checks that associated recordings with not-zero duration exist for each booking ID in final SA.
 * Posts Slack message with missing or zero-duration recording IDs
 */
@Component
@Slf4j
public class CheckForMissingRecordings extends RobotUserTask {

    private final CaptureSessionService captureSessionService;
    private final RecordingService recordingService;
    private final SlackClient slackClient;
    private final String platformEnv;

    @Autowired
    CheckForMissingRecordings(CaptureSessionService captureSessionService,
                              SlackClient slackClient,
                              UserService userService,
                              UserAuthenticationService userAuthenticationService,
                              @Value("${cron-user-email}") String cronUserEmail,
                              RecordingService recordingService,
                              @Value("${platform-env}") String platformEnv) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.captureSessionService = captureSessionService;
        this.slackClient = slackClient;
        this.recordingService = recordingService;
        this.platformEnv = platformEnv;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Signing in robot user with email {} on env {}", cronUserEmail, platformEnv);
        signInRobotUser();

        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Running CheckForMissingRecordings task: looking for missing recordings from {}", yesterday);

        List<CaptureSession> captureSessionsFromDate = captureSessionService.findAvailableSessionsByDate(yesterday);

        if (!captureSessionsFromDate.isEmpty()) {
            log.info("Expecting to find {} recordings", captureSessionsFromDate.size());

            var search = new SearchRecordings();
            search.setStartedAtFrom(Timestamp.valueOf(yesterday.atStartOfDay()));
            search.setStartedAtUntil(Timestamp.valueOf(yesterday.atStartOfDay().plusDays(1)));
            search.setIncludeDeleted(false);
            var recordings = recordingService.findAll(search, false, Pageable.unpaged());

            Map<UUID, Duration> recordingDuration = new HashMap<>();

            recordings.forEach(recordingDTO -> recordingDuration.put(
                recordingDTO.getId(),
                recordingDTO.getDuration()
            ));

            log.info("Found {} recordings", recordingDuration.size());

            List<String> missingRecordings = new ArrayList<>();
            List<String> zeroDurationRecordings = new ArrayList<>();

            for (CaptureSession captureSession : captureSessionsFromDate) {
                if (captureSession.getBooking() == null) {
                    log.error("Unexpected capture session {} without booking", captureSession.getId());
                }
                UUID bookingID = captureSession.getBooking().getId();
                if (bookingID == null) {
                    log.error("Unexpected capture session {} without booking ID", captureSession.getId());
                } else if (recordingDuration.get(bookingID) == null) {
                    missingRecordings.add(bookingID.toString());
                } else if (recordingDuration.get(bookingID) == Duration.ZERO) {
                    zeroDurationRecordings.add(bookingID.toString());
                }
            }

            List<SlackMessageSection> sections = new ArrayList<>();
            if (!missingRecordings.isEmpty()) {
                log.info("Found missing recordings: {}\nSending alert to Slack channel...", missingRecordings);
                SlackMessageSection missing = new SlackMessageSection(
                    "Missing Recordings", missingRecordings,
                    "Expected to find recordings in final storage account"
                );
                sections.add(missing);
            }
            if (!zeroDurationRecordings.isEmpty()) {
                log.info(
                    "Found zero-duration recordings: {}\nSending alert to Slack channel...",
                    zeroDurationRecordings
                );
                SlackMessageSection zeroed = new SlackMessageSection(
                    "Zero-Duration Recordings", zeroDurationRecordings,
                    "Recordings in final storage account had zero duration"
                );
                sections.add(zeroed);
            }

            if (!sections.isEmpty()) {
                SlackMessage slackMessage = SlackMessage.builder()
                    .environment(platformEnv)
                    .sections(sections)
                    .build();

                slackClient.postSlackMessage(slackMessage.toJson());
            }

        }

        log.info("Completed CheckForMissingRecordings task");
    }
}

