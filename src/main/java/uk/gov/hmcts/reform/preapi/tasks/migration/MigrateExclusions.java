package uk.gov.hmcts.reform.preapi.tasks.migration;

import org.springframework.stereotype.Component;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.MigrationType;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@Component("migrateExclusions")
public class MigrateExclusions extends BaseTask {

    private final Job processExclusionsJob;

    public MigrateExclusions(UserService userService,
                             UserAuthenticationService userAuthenticationService,
                             @Value("${cron-user-email}") String cronUserEmail,
                             JobLauncher jobLauncher,
                             LoggingService loggingService,
                             @Value("${migration.debug}") boolean debug,
                             @Qualifier("processExclusionsJob") Job processExclusionsJob) {
        super(userService, userAuthenticationService, cronUserEmail, jobLauncher, loggingService, debug);
        this.processExclusionsJob = processExclusionsJob;
    }

    @Override
    public void run() throws RuntimeException {
        startJob(processExclusionsJob, "Process Exclusions", MigrationType.FULL);
    }
}
