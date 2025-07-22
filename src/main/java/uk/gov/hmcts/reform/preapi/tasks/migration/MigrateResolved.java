package uk.gov.hmcts.reform.preapi.tasks.migration;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.MigrationType;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@Component("migrateResolved")
public class MigrateResolved extends BaseTask {

    private final Job resolvedMigrationRecordJob;

    public MigrateResolved(UserService userService,
                             UserAuthenticationService userAuthenticationService,
                             @Value("${cron-user-email}") String cronUserEmail,
                             JobLauncher jobLauncher,
                             LoggingService loggingService,
                             @Value("${migration.debug}") boolean debug,
                             @Value("${migration.dry-run:false}") boolean dryRun,
                             @Qualifier("resolvedMigrationRecordJob") Job resolvedMigrationRecordJob 
    ) {
        super(userService, userAuthenticationService, cronUserEmail, jobLauncher, loggingService, debug, dryRun);
        this.resolvedMigrationRecordJob = resolvedMigrationRecordJob;
    }

    @Override
    public void run() throws RuntimeException {
        startJob(resolvedMigrationRecordJob, "Process Resolved Migration Records", MigrationType.FULL);
    }
}
