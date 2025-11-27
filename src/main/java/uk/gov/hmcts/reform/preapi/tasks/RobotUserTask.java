package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@Slf4j
public abstract class RobotUserTask implements Runnable {
    protected final UserService userService;
    protected final UserAuthenticationService userAuthenticationService;
    protected final String cronUserEmail;

    protected RobotUserTask(UserService userService,
                            UserAuthenticationService userAuthenticationService,
                            @Value("${cron-user-email}") String cronUserEmail) {
        this.userService = userService;
        this.userAuthenticationService = userAuthenticationService;
        this.cronUserEmail = cronUserEmail;
    }

    protected void signInRobotUser() {
        log.info("Sign in as robot user");
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
