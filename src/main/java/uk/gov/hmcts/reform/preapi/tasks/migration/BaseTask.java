package uk.gov.hmcts.reform.preapi.tasks.migration;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.MigrationType;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.tasks.RobotUserTask;

@Component
public abstract class BaseTask extends RobotUserTask {

    private final JobLauncher jobLauncher;
    private final LoggingService loggingService;
    private final boolean debug;

    @Autowired
    public BaseTask(UserService userService,
                    UserAuthenticationService userAuthenticationService,
                    @Value("${cron-user-email}") String cronUserEmail,
                    JobLauncher jobLauncher, LoggingService loggingService,
                    @Value("${migration.debug}") boolean debug) {

        super(userService, userAuthenticationService, cronUserEmail);
        this.jobLauncher = jobLauncher;
        this.loggingService = loggingService;
        this.debug = debug;
    }

    protected void startJob(Job job, String jobName, MigrationType migrationType) {
        try {
            var jobParametersBuilder = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("debug", String.valueOf(debug))
                .addString("migrationType", migrationType.name());

            jobLauncher.run(job, jobParametersBuilder.toJobParameters());
            loggingService.logInfo("Successfully completed " + jobName + " batch job");

        } catch (Exception e) {
            loggingService.logError("Error starting " + jobName + " batch job", e);
        }
    }
}
