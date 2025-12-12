package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.time.LocalDate;
import java.util.List;

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
        correctCaptureSessionStatuses(unusedSessions);
        log.info("Correction task completed. {} sessions updated.", unusedSessions.size());
    }

    public List<CaptureSession> getFailedCaptureSessions() {
        return captureSessionService.findFailedCaptureSessionsStartedBetween(THRESHOLD_DATE, LocalDate.now());
    }

    public List<CaptureSession> filterUnusedCaptureSessionsBySectionFile(List<CaptureSession> captureSessions) {

        return captureSessions.stream()
            .filter(session -> session != null && session.getBooking() != null)
            .filter(session -> {
                String containerName = String.valueOf(session.getBooking().getId());
                return !azureIngestStorageService.sectionFileExist(containerName);
            })
            .toList();
    }

    public void correctCaptureSessionStatuses(List<CaptureSession> captureSessions) {
        captureSessions.forEach(session -> {
            session.setStatus(RecordingStatus.NO_RECORDING);
            captureSessionService.upsert(new CreateCaptureSessionDTO(session));
            log.info("Capture Session {} detected as unused. Marked status as NO_RECORDING", session.getId());
        });
    }
}
