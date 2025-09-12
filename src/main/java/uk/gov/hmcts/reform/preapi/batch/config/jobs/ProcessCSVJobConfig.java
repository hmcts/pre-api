package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
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
public class ProcessCSVJobConfig {
    private final JobRepository jobRepository;
    public final PlatformTransactionManager transactionManager;
    private final BatchConfiguration batchConfiguration;
    private final CoreStepsConfig coreSteps;

    public ProcessCSVJobConfig(final JobRepository jobRepository,
                               final PlatformTransactionManager transactionManager,
                               final BatchConfiguration batchConfiguration,
                               final CoreStepsConfig coreSteps) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
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
        @Qualifier("pendingMigrationRecordStep") Step pendingMigrationRecordStep
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
            .next(createPreProcessMetadataStep)
            .next(pendingMigrationRecordStep)
            .next(createWriteToCSVStep)
            .end()
            .build();
    }

    @Bean
    @JobScope
    public Step pendingMigrationRecordStep(ListItemReader<MigrationRecord> pendingMigrationRecordReader,
                                           ItemProcessor<Object, MigratedItemGroup> processor,
                                           ItemWriter<MigratedItemGroup> writer) {
        return new StepBuilder("pendingMigrationRecordStep", jobRepository)
            .<MigrationRecord, MigratedItemGroup>chunk(BatchConfiguration.CHUNK_SIZE, transactionManager)
            .reader(pendingMigrationRecordReader)
            .processor(processor)
            .writer(coreSteps.getDryRunFlag() ? coreSteps.noOpWriter() : writer)
            .faultTolerant()
            .skipLimit(BatchConfiguration.SKIP_LIMIT)
            .skip(Exception.class)
            .listener(new StepExecutionListener() {
                @Override
                public void beforeStep(StepExecution stepExecution) {
                    System.out.println("[STEP-START] pendingMigrationRecordStep");
                }

                @Override
                public ExitStatus afterStep(StepExecution stepExecution) {
                    System.out.println(
                        String.format("[STEP-END] pendingMigrationRecordStep status=%s read=%d write=%d skips=%d",
                            stepExecution.getExitStatus(),
                            stepExecution.getReadCount(),
                            stepExecution.getWriteCount(),
                            stepExecution.getSkipCount()
                        )
                    );
                    return stepExecution.getExitStatus();
                }
            })
            .build();
    }

    @Bean
    @StepScope
    public ListItemReader<MigrationRecord> pendingMigrationRecordReader(MigrationRecordRepository repository,
                                                                        LoggingService loggingService) {
        List<MigrationRecord> pending = repository.findAllByStatus(VfMigrationStatus.PENDING);
        loggingService.startRun("pending migration records", pending.size());
        
        if (pending.isEmpty()) {
            loggingService.logInfo("No pending migration records found.");
        } else {
            loggingService.logInfo("Found %d pending migration records.", pending.size());
        }
        return new ListItemReader<>(pending);
    }
}
