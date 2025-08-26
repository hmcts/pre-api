package uk.gov.hmcts.reform.preapi.tasks.migration;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@Component("processMigration")
public class ProcessMigration extends BaseTask {

    private final Job processCSVJob;

    public ProcessMigration(UserService userService,
                            UserAuthenticationService userAuthenticationService,
                            @Value("${cron-user-email}") String cronUserEmail,
                            JobLauncher jobLauncher,
                            LoggingService loggingService,
                            @Value("${migration.debug}") boolean debug,
                            @Value("${migration.dry-run:false}") boolean dryRun,
                            @Qualifier("processCSVJob") Job processCSVJob) {
        super(userService, userAuthenticationService, cronUserEmail, jobLauncher, loggingService, debug, dryRun);
        this.processCSVJob = processCSVJob;
    }

    @Override
    public void run() {
        startJob(processCSVJob, "Transform");
    }
}
