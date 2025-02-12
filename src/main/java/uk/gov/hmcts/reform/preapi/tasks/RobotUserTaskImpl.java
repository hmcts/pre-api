package uk.gov.hmcts.reform.preapi.tasks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@Component
public class RobotUserTaskImpl extends RobotUserTask {

    public RobotUserTaskImpl(UserService userService,
                             UserAuthenticationService userAuthenticationService,
                             @Value("${vodafone-user-email}") String cronUserEmail) {
        super(userService, userAuthenticationService, cronUserEmail);
    }

    public void signIn() {
        signInRobotUser();
    }

    @Override
    public void run() {
    }
}