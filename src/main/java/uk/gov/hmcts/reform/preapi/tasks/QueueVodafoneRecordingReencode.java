package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingReencodeJobService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class QueueVodafoneRecordingReencode extends RobotUserTask {
    private final RecordingReencodeJobService recordingReencodeJobService;
    private final String recordingIds;

    @Autowired
    public QueueVodafoneRecordingReencode(RecordingReencodeJobService recordingReencodeJobService,
                                          UserService userService,
                                          UserAuthenticationService userAuthenticationService,
                                          @Value("${cron-user-email}") String cronUserEmail,
                                          @Value("${tasks.vf-recording-reencode.recording-ids}")
                                              String recordingIds) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.recordingReencodeJobService = recordingReencodeJobService;
        this.recordingIds = recordingIds;
    }

    @Override
    public void run() {
        signInRobotUser();

        List<UUID> parsedRecordingIds = parseRecordingIds(recordingIds);
        if (parsedRecordingIds.isEmpty()) {
            log.info("No recording ids supplied for Vodafone re-encode queue task");
            return;
        }

        int queued = recordingReencodeJobService.queueJobs(parsedRecordingIds);
        log.info("Queued {} Vodafone recording re-encode job(s)", queued);
    }

    protected List<UUID> parseRecordingIds(String ids) {
        return Arrays.stream(ids.split(","))
            .map(String::trim)
            .filter(id -> !id.isBlank())
            .map(UUID::fromString)
            .toList();
    }
}
