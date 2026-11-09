package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

/**
 * Decides whether to make *VF original recordings* visible, depending on HIDE_REENCODED_RECORDINGS flag
 * Needs to be run after flag has been updated to hide/display VF recordings
 * Re-encoded recordings' visibility is controlled directly by the flag in application code
 * This approach uses the existing logic around deleted recordings to control visibility instead.
 * It is intended as a temporary measure while we monitor re-encoded recordings for any issues.
 * If all is well, VF originals can remain deleted, and we can delete this task.
 */
@Slf4j
@Component
public class UpdateRecordingsVisibility extends RobotUserTask {

    private final boolean hideReencodedRecordings;
    private final RecordingService recordingService;

    @Autowired
    public UpdateRecordingsVisibility(RecordingService recordingService,
                                      UserService userService,
                                      UserAuthenticationService userAuthenticationService,
                                      @Value("${cron-user-email}") String cronUserEmail,
                                      @Value("${HIDE_REENCODED_RECORDINGS:}") String hideReencodedRecordings) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.recordingService = recordingService;
        this.hideReencodedRecordings = Boolean.parseBoolean(hideReencodedRecordings);
    }

    @Override
    public void run() {
        signInRobotUser();

        if (hideReencodedRecordings) {
            log.info("Re-encoded recordings are hidden. Undeleting originals...");
            recordingService.undeleteOriginalWhereReencodedVersionExists();
        } else {
            recordingService.deleteOriginalWhereReencodedVersionExists();
        }
    }

}
