package uk.gov.hmcts.reform.preapi.tasks.migration;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@Component("fetchData")
public class FetchData extends BaseTask {

    private final String sourceType;
    private final Job fetchDataJob;

    public FetchData(UserService userService,
                    UserAuthenticationService userAuthenticationService,
                    @Value("${vodafone-user-email}") String cronUserEmail,
                    JobLauncher jobLauncher,
                    LoggingService loggingService,
                    @Value("${migration.debug}") boolean debug,
                    @Value("${migration.dry-run:false}") boolean dryRun,
                    @Value("${migration.source-type:xml}") String sourceType,
                    @Qualifier("fetchDataJob") Job fetchDataJob) {
        super(userService, userAuthenticationService, cronUserEmail, jobLauncher, loggingService, debug, dryRun);
        this.sourceType = sourceType;
        this.fetchDataJob = fetchDataJob;
    }

    @Override
    public void run() throws RuntimeException {
        startJob(fetchDataJob, "Fetch Data", "sourceType", sourceType);
    }
}
