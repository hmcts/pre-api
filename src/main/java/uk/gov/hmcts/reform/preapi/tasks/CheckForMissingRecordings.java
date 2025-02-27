package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessage;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessageSection;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CheckForMissingRecordings implements Runnable {

    private final CaptureSessionService captureSessionService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final SlackClient slackClient;
    private final String platformEnv;

    @Autowired
    CheckForMissingRecordings(CaptureSessionService captureSessionService,
                              AzureFinalStorageService azureFinalStorageService,
                              SlackClient slackClient,
                              @Value("${platform-env}") String platformEnv) {
        this.captureSessionService = captureSessionService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.slackClient = slackClient;
        this.platformEnv = platformEnv;
    }

    @Override
    public void run() {
        log.info("Running CheckForMissingRecordings task");

        Timestamp yesterday = Timestamp.valueOf(LocalDateTime.now().minusDays(1));
        List<CaptureSession> captureSessions = captureSessionService.findByDate(yesterday);

        List<String> missingBookingIds = captureSessions.stream()
                .map(CaptureSession::getBooking)
                .map(Booking::getId)
                .filter(bookingId ->
                        azureFinalStorageService.getRecordingDuration(bookingId).equals(Duration.ZERO))
                .map(UUID::toString)
                .toList();

        SlackMessageSection section = new SlackMessageSection("Missing Recordings", missingBookingIds,
                "Expected to find recordings in final storage account");

        SlackMessage slackMessage = SlackMessage.builder()
                .environment(platformEnv)
                .sections(List.of(section))
                .build();

        slackClient.postSlackMessage(slackMessage.toJson());

        log.info("Completed CheckForStreamingLocatorsAndLiveEvents task");
    }
}

