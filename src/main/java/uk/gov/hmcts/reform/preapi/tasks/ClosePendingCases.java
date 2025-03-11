package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@Slf4j
@Component
public class ClosePendingCases extends RobotUserTask {

    private final CaseService caseService;

    @Autowired
    public ClosePendingCases(CaseService caseService,
                             UserService userService,
                             UserAuthenticationService userAuthenticationService,
                             @Value("${cron-user-email}") String cronUserEmail) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.caseService = caseService;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Running ClosePendingCases task");

        try {
            caseService.closePendingCases();
            log.info("Successfully closed pending cases");
        } catch (Exception e) {
            log.error("Failed to close pending cases", e);
        }

        log.info("Completed ClosePendingCases task");
    }
}
