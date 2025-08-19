package uk.gov.hmcts.reform.preapi.tasks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;


@Component
public class BatchRobotUserTask extends RobotUserTask {

    private final LoggingService loggingService;

    public BatchRobotUserTask(
        @Value("${vodafone-user-email}") String cronUserEmail,
        UserService userService,
        UserAuthenticationService userAuthenticationService,
        LoggingService loggingService
    ) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.loggingService = loggingService;
    }

    public void signIn() {
        loggingService.logInfo("Sign in as robot user");
        signInRobotUser();
    }

    @Override
    public void run() {
        // this method is left empty because BatchRobotUserTask does not require a scheduled run.
    }
}
