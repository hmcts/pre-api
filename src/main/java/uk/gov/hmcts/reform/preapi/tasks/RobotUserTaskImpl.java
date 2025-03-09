package uk.gov.hmcts.reform.preapi.tasks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;
 

@Component
public class RobotUserTaskImpl extends RobotUserTask {

    public RobotUserTaskImpl(
        @Value("${vodafone-user-email}") String cronUserEmail,
        UserService userService,
        UserAuthenticationService userAuthenticationService,
        LoggingService loggingService
    ) {
        super(cronUserEmail, userService, userAuthenticationService, loggingService);
    }

    public void signIn() {
        signInRobotUser();
    }

    @Override
    public void run() {
    }
}