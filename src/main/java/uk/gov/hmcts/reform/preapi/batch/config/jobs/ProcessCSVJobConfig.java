package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;

@Configuration
public class ProcessCSVJobConfig {

    private final JobRepository jobRepository;
    private final BatchConfiguration batchConfiguration;
    private final CoreStepsConfig coreSteps;


    public ProcessCSVJobConfig(
        JobRepository jobRepository,
        BatchConfiguration batchConfiguration,
        CoreStepsConfig coreSteps
    ) {
        this.jobRepository = jobRepository;
        this.batchConfiguration = batchConfiguration;
        this.coreSteps = coreSteps;
    }

    @Bean
    public Job processCSVJob(
        @Qualifier("createSitesDataStep") Step createSitesDataStep,
        @Qualifier("createChannelUserStep") Step createChannelUserStep,
        @Qualifier("createRobotUserSignInStep") Step createRobotUserSignInStep,
        @Qualifier("createPreProcessStep") Step createPreProcessStep,
        @Qualifier("createPreProcessMetadataStep") Step createPreProcessMetadataStep,
        @Qualifier("createArchiveListStep") Step createArchiveListStep,
        @Qualifier("createWriteToCSVStep") Step createWriteToCSVStep,
        @Qualifier("createDeltaProcessingStep") Step createDeltaProcessingStep,
        @Qualifier("createDeltaListStep") Step createDeltaListStep
    ) {
        return new JobBuilder("processCSVJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(batchConfiguration.fileAvailabilityDecider())
            .on("FAILED").end()
            .on("COMPLETED")
            
            .to(coreSteps.startLogging())
            .next(createSitesDataStep)
            .next(createChannelUserStep)
            .next(createRobotUserSignInStep)
            .next(createPreProcessStep)

            .next(batchConfiguration.deltaProcessingDecider())
            .on("FULL").to(createPreProcessMetadataStep)
                          .next(createArchiveListStep)
                          .next(createWriteToCSVStep)
            .from(batchConfiguration.deltaProcessingDecider())
            .on("DELTA").to(createDeltaProcessingStep)
                          .next(createDeltaListStep)
                          .next(createWriteToCSVStep)
            .end()
            .build();
    }
}