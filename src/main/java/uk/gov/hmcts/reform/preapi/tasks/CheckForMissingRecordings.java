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
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;


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
    private final AzureFinalStorageService azureFinalStorageService;

    @Autowired
    CheckForMissingRecordings(CaptureSessionService captureSessionService,
                              SlackClient slackClient,
                              UserService userService,
                              UserAuthenticationService userAuthenticationService,
                              @Value("${cron-user-email}") String cronUserEmail,
                              RecordingService recordingService,
                              @Value("${platform-env}") String platformEnv,
                              AzureFinalStorageService azureFinalStorageService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.captureSessionService = captureSessionService;
        this.slackClient = slackClient;
        this.recordingService = recordingService;
        this.platformEnv = platformEnv;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Signing in robot user with email {} on env {}", cronUserEmail, platformEnv);
        signInRobotUser();

        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Running CheckForMissingRecordings task: looking for missing recordings from {}", yesterday);

        Map<RecordingStatus, List<String>> captureSessionIds = captureSessionService.findAvailableSessionsByDate(
                yesterday)
            .stream()
            .filter(captureSession ->
                        captureSession.getStatus() != RecordingStatus.STANDBY
                            && captureSession.getStatus() != RecordingStatus.INITIALISING)
            .collect(groupingBy(
                CaptureSession::getStatus,
                mapping(captureSession -> captureSession.getId().toString(), toList())
            ));

        List<SlackMessageSection> sections = getSlackMessageSections(captureSessionIds);

        List<String> captureSessionsWithAvailableRecordings =
            captureSessionIds.get(RecordingStatus.RECORDING_AVAILABLE);
        if (captureSessionsWithAvailableRecordings != null && !captureSessionsWithAvailableRecordings.isEmpty()) {
            sections.addAll(checkRecordingsAreAvailable(captureSessionsWithAvailableRecordings, yesterday));
        }

        if (!sections.isEmpty()) {
            SlackMessage slackMessage = SlackMessage.builder()
                .environment(platformEnv)
                .sections(sections)
                .build();

            log.info("About to send slack notification");
            slackClient.postSlackMessage(slackMessage.toJson());
        }

        log.info("Completed CheckForMissingRecordings task");
    }

    private Map<String, RecordingDTO> getRecordingsFromDate(LocalDate yesterday) {
        var search = new SearchRecordings();
        search.setStartedAtFrom(Timestamp.valueOf(yesterday.atStartOfDay()));
        search.setStartedAtUntil(Timestamp.valueOf(yesterday.atStartOfDay().plusDays(1)));
        search.setIncludeDeleted(false);
        return recordingService.findAll(search, false, Pageable.unpaged()).stream()
            .filter(recordingDTO -> recordingDTO.getCaptureSession() != null)
            .collect(Collectors.toMap(
                recordingDTO -> recordingDTO.getCaptureSession().getId().toString(),
                recordingDTO -> recordingDTO
            ));
    }


    private List<SlackMessageSection> checkRecordingsAreAvailable(List<String> captureSessionIds,
                                                                  LocalDate yesterday) {
        // Map<Capture Session ID: RecordingDTO>
        Map<String, RecordingDTO> recordingsFromDate = getRecordingsFromDate(yesterday);

        List<String> unhappyRecordings = new ArrayList<>();
        for (String captureSessionId : captureSessionIds) {
            var recording = recordingsFromDate.get(captureSessionId);
            if (recording == null) {
                unhappyRecordings.add(format(
                    "Missing recording for capture session %s: not in database",
                    captureSessionId
                ));
            } else if (recording.getDuration() == Duration.ZERO) {
                unhappyRecordings.add(format(
                    "Recording for capture session %s has zero duration in database",
                    captureSessionId
                ));
            } else if (recording.getCaptureSession().getLiveOutputUrl() == null) {
                unhappyRecordings.add(format(
                    "Capture session %s missing live output url",
                    captureSessionId
                ));
            } else {
                var recordingFromFinalSA = azureFinalStorageService.getRecordingDuration(recording.getId());
                if (recordingFromFinalSA == null) {
                    unhappyRecordings.add(format(
                        "Missing recording for capture session %s: not in final SA",
                        captureSessionId
                    ));
                }
            }
        }

        SlackMessageSection slackMessageSection = new SlackMessageSection(
            "Capture sessions: RECORDING_AVAILABLE but with problems",
            unhappyRecordings, ""
        );
        return List.of(slackMessageSection);
    }

    private static List<SlackMessageSection> getSlackMessageSections(Map<RecordingStatus,
        List<String>> captureSessions) {
        List<SlackMessageSection> sections = new ArrayList<>();

        for (RecordingStatus status : captureSessions.keySet()) {
            if (status == RecordingStatus.RECORDING_AVAILABLE
                || status == RecordingStatus.INITIALISING
                || status == RecordingStatus.STANDBY) {
                continue; // Don't alert for successful or unstarted captures
            }
            SlackMessageSection slackMessageSection = new SlackMessageSection(
                format("Capture sessions with %s status", status.toString()),
                captureSessions.get(status), ""
            );
            sections.add(slackMessageSection);
        }

        return sections;
    }
}

