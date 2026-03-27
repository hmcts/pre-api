package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.RecordingReencodeJob;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingReencodeJobService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.services.VodafoneRecordingReencodeService;

@Slf4j
@Component
public class PerformVodafoneRecordingReencode extends RobotUserTask {
    private final RecordingReencodeJobService recordingReencodeJobService;
    private final VodafoneRecordingReencodeService vodafoneRecordingReencodeService;

    @Autowired
    public PerformVodafoneRecordingReencode(RecordingReencodeJobService recordingReencodeJobService,
                                            VodafoneRecordingReencodeService vodafoneRecordingReencodeService,
                                            UserService userService,
                                            UserAuthenticationService userAuthenticationService,
                                            @org.springframework.beans.factory.annotation.Value("${cron-user-email}")
                                                String cronUserEmail) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.recordingReencodeJobService = recordingReencodeJobService;
        this.vodafoneRecordingReencodeService = vodafoneRecordingReencodeService;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Running PerformVodafoneRecordingReencode task");

        recordingReencodeJobService.getNextPendingJob()
            .ifPresentOrElse(
                this::attemptPerformReencode,
                () -> log.info("No pending Vodafone recording re-encode jobs found")
            );
    }

    private void attemptPerformReencode(RecordingReencodeJob job) {
        try {
            RecordingReencodeJob lockedJob = recordingReencodeJobService.markAsProcessing(job.getId());
            vodafoneRecordingReencodeService.processJob(lockedJob);
            recordingReencodeJobService.markAsComplete(lockedJob.getId());
        } catch (PessimisticLockingFailureException | ResourceInWrongStateException e) {
            log.info("Skipping re-encode job {}, already reserved by another process", job.getId());
        } catch (Exception e) {
            log.error("Error while re-encoding recording for job {}", job.getId(), e);
            recordingReencodeJobService.markAsError(job.getId(), e.getMessage());
        }
    }
}
