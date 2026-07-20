package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;

@Slf4j
@Component
public class DeleteOriginalVodafoneRecordings extends RobotUserTask {

    private final boolean hideReencodedRecordings;
    private final RecordingService recordingService;

    public DeleteOriginalVodafoneRecordings(RecordingService recordingService,
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
        if (hideReencodedRecordings) {
            log.info("Re-encoded recordings are hidden. Quitting...");
            return;
        }
        signInRobotUser();

        List<RecordingDTO> allVodafoneRecordings = recordingService.findAllVodafoneRecordings();

        allVodafoneRecordings.stream()
            .filter(r -> recordingService.reencodedVersionExists(r.getId()))
            .forEach(r -> recordingService.deleteById(r.getId()));
    }

}
