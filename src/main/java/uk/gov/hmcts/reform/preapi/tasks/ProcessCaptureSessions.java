package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ProcessingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@Slf4j
@Component
public class ProcessCaptureSessions extends RobotUserTask {
    private final ProcessingService processingService;

    protected ProcessCaptureSessions(final UserService userService,
                                     final UserAuthenticationService userAuthenticationService,
                                     final ProcessingService processingService,
                                     @Value("${cron-user-email}") String cronUserEmail) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.processingService = processingService;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Starting process capture session task");

        processingService.processAllCaptureSessions();

        log.info("Finished process capture session task");
    }

}
