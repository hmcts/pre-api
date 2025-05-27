package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;

@Configuration
public class ExclusionJobConfig {

    private final JobRepository jobRepository;
    private final CoreStepsConfig coreSteps;

    public ExclusionJobConfig(
        JobRepository jobRepository,
        CoreStepsConfig coreSteps
    ) {
        this.jobRepository = jobRepository;
        this.coreSteps = coreSteps;
    }

    @Bean
    public Job processExclusionsJob(
        @Qualifier("createSitesDataStep") Step createSitesDataStep,
        @Qualifier("createChannelUserStep") Step createChannelUserStep,
        @Qualifier("createRobotUserSignInStep") Step createRobotUserSignInStep,
        @Qualifier("createPreProcessStep") Step createPreProcessStep,
        @Qualifier("createExemptionListStep") Step createExemptionListStep,
        @Qualifier("createWriteToCSVStep") Step createWriteToCSVStep
    ) {
        return new JobBuilder("processExclusionsJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(coreSteps.startLogging())
            .next(createSitesDataStep)
            .next(createChannelUserStep)
            .next(createRobotUserSignInStep)
            .next(createPreProcessStep)
            .next(createExemptionListStep)
            .next(createWriteToCSVStep)
            .build();
    }
}
