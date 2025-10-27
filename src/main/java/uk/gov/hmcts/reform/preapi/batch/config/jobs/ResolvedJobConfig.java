package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;

import java.util.List;

@Configuration
public class ResolvedJobConfig {
    private final JobRepository jobRepository;
    public final PlatformTransactionManager transactionManager;
    private final CoreStepsConfig coreSteps;

    public ResolvedJobConfig(final JobRepository jobRepository,
                             final CoreStepsConfig coreSteps,
                             final PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.coreSteps = coreSteps;
        this.transactionManager = transactionManager;
    }

    @Bean
    @StepScope
    public ListItemReader<MigrationRecord> resolvedMigrationRecordReader(MigrationRecordRepository repository,
                                                                         LoggingService loggingService) {
        List<MigrationRecord> resolved = repository.findAllByStatusOrderedByVersion(VfMigrationStatus.SUBMITTED);
        if (resolved.isEmpty()) {
            loggingService.logInfo("No resolved migration records found.");
        } else {
            loggingService.logInfo("Found %d resolved migration records.", resolved.size());
        }

        return new ListItemReader<>(resolved);
    }

    @Bean
    @JobScope
    public Step resolvedMigrationRecordStep(ListItemReader<MigrationRecord> resolvedMigrationRecordReader,
                                            ItemProcessor<Object, MigratedItemGroup> processor,
                                            ItemWriter<MigratedItemGroup> writer) {
        return new StepBuilder("resolvedMigrationRecordStep", jobRepository)
            .<MigrationRecord, MigratedItemGroup>chunk(BatchConfiguration.CHUNK_SIZE, transactionManager)
            .reader(resolvedMigrationRecordReader)
            .processor(processor)
            .writer(coreSteps.getDryRunFlag() ? coreSteps.noOpWriter() : writer)
            .faultTolerant()
            .skipLimit(BatchConfiguration.SKIP_LIMIT)
            .skip(Exception.class)
            .build();
    }

    @Bean(name = "resolvedMigrationRecordJob")
    public Job resolvedMigrationRecordJob(@Qualifier("createSitesDataStep") Step createSitesDataStep,
                                          @Qualifier("createChannelUserStep") Step createChannelUserStep,
                                          @Qualifier("createPreProcessStep") Step createPreProcessStep,
                                          @Qualifier("createRobotUserSignInStep") Step createRobotUserSignInStep,
                                          @Qualifier("resolvedMigrationRecordStep") Step resolvedMigrationRecordStep,
                                          @Qualifier("createWriteToCSVStep") Step createWriteToCSVStep) {
        return new JobBuilder("resolvedMigrationRecordJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(coreSteps.startLogging())
            .next(createSitesDataStep)
            .next(createChannelUserStep)
            .next(createRobotUserSignInStep)
            .next(createPreProcessStep)
            .next(resolvedMigrationRecordStep)
            .next(createWriteToCSVStep)
            .build();
    }
}
