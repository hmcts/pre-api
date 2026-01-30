package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One off task to correct the status of capture sessions that were wrongly marked with the status of failure
 * when they were actually unused.
 */
@Component
@Slf4j
public class CaptureSessionStatusCorrectionTask extends RobotUserTask {

    private final CaptureSessionService captureSessionService;
    private final AzureIngestStorageService azureIngestStorageService;

    private static final LocalDate THRESHOLD_DATE = LocalDate.of(2025, 9, 28);

    @Autowired
    public CaptureSessionStatusCorrectionTask(UserService userService,
                                              UserAuthenticationService userAuthenticationService,
                                              @Value("${cron-user-email}") String cronUserEmail,
                                              AzureIngestStorageService azureIngestStorageService,
                                              CaptureSessionService captureSessionService
    ) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.azureIngestStorageService = azureIngestStorageService;
        this.captureSessionService = captureSessionService;
    }

    @Override
    public void run() {
        log.info("Starting capture session status correction task");
        signInRobotUser();
        List<CaptureSession> captureSessionsMarkedFailure = getFailedCaptureSessions();
        List<CaptureSession> unusedSessions = filterUnusedCaptureSessionsBySectionFile(captureSessionsMarkedFailure);
        if (!unusedSessions.isEmpty()) {
            correctCaptureSessionStatuses(unusedSessions);
        } else {
            log.info("No unused capture sessions found.");
        }
        log.info("Correction task completed");
    }

    private List<CaptureSession> getFailedCaptureSessions() {
        return captureSessionService.findFailedCaptureSessionsStartedBetween(THRESHOLD_DATE, LocalDate.now());
    }

    private List<CaptureSession> filterUnusedCaptureSessionsBySectionFile(List<CaptureSession> captureSessions) {

        return captureSessions.stream()
            .filter(session -> session != null && session.getBooking() != null)
            .filter(session -> {
                String containerName = String.valueOf(session.getBooking().getId());
                return !azureIngestStorageService.sectionFileExist(containerName);
            })
            .toList();
    }

    private void correctCaptureSessionStatuses(List<CaptureSession> captureSessions) {
        List<UUID> erroredSessionIds = new ArrayList<>();
        List<UUID> successfulSessionIds = new ArrayList<>();
        captureSessions.forEach(session -> {
            try {
                session.setStatus(RecordingStatus.NO_RECORDING);
                captureSessionService.save(session);
                successfulSessionIds.add(session.getId());
            } catch (Exception e) {
                erroredSessionIds.add(session.getId());
            }
        });
        log.info(
            "{} out of {} capture sessions processed were updated without errors. Successful capture session IDs: {}",
                 successfulSessionIds.size(), captureSessions.size(),
            StringUtils.join(successfulSessionIds, ", "));
        if (!erroredSessionIds.isEmpty()) {
            log.error(
                "Failed to update {} capture sessions. Errored capture session IDs: {}",
                erroredSessionIds.size(), StringUtils.join(erroredSessionIds, ", ")
            );
        }
    }
}
