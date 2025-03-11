package uk.gov.hmcts.reform.preapi.tasks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;

import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

public abstract class RobotUserTask implements Runnable {
    protected final String cronUserEmail;
    protected final UserService userService;
    protected final UserAuthenticationService userAuthenticationService;
    private final LoggingService loggingService;

    protected RobotUserTask(
        UserService userService,
        UserAuthenticationService userAuthenticationService,
        @Value("${cron-user-email}") String cronUserEmail,
        LoggingService loggingService 
    ) {
        this.userService = userService;
        this.userAuthenticationService = userAuthenticationService;
        this.cronUserEmail = cronUserEmail;
        this.loggingService = loggingService; 
    }

    protected void signInRobotUser() {
        loggingService.logInfo("Sign in as robot user");        
        var user = userService.findByEmail(cronUserEmail);
      
        var appAccess = user.getAppAccess().stream().findFirst()
            .orElseThrow(() -> new RuntimeException(
                "Failed to authenticate as cron user with email " + cronUserEmail)
            );
        var userAuth = userAuthenticationService.validateUser(appAccess.getId().toString())
            .orElseThrow(() -> new RuntimeException(
                "Failed to authenticate as cron user with email "
                    + cronUserEmail)
            );
        SecurityContextHolder.getContext().setAuthentication(userAuth);
    }
}
