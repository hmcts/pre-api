package uk.gov.hmcts.reform.preapi.batch.config.steps;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.JobSynchronizationManager;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.MigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.config.MigrationType;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;

import java.io.IOException;
import java.util.Optional;

@Configuration
public class CoreStepsConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final Processor itemProcessor;
    private final MigrationWriter itemWriter;
    private final LoggingService loggingService;

    public CoreStepsConfig(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        Processor itemProcessor,
        MigrationWriter itemWriter,
        LoggingService loggingService
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.itemProcessor = itemProcessor;
        this.itemWriter = itemWriter;
        this.loggingService = loggingService;
    }

    @Bean
    @JobScope
    public Step createSitesDataStep(@Value("#{jobParameters['dryRun']}") String dryRun) {
        return createReadStep(
            "sitesDataStep",
            new ClassPathResource(BatchConfiguration.SITES_CSV),
            new String[]{"site_reference", "site_name", "location", "court_name"},
            CSVSitesData.class,
            false,
            Boolean.parseBoolean(dryRun)
        );
    }

    @Bean
    @JobScope
    public Step createChannelUserStep() {
        return createReadStep(
            "channelUserStep",
            new ClassPathResource(BatchConfiguration.CHANNEL_USER_CSV),
            new String[]{"channel_name", "channel_user", "channel_user_email"},
            CSVChannelData.class,
            false,
            getDryRunFlag()
        );
    }

    public Step startLogging() {
        return new StepBuilder("loggingStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    String debugParam = (String) chunkContext.getStepContext()
                                                             .getJobParameters()
                                                             .get("debug");

                    var migrationType = MigrationType.fromString((String) chunkContext.getStepContext()
                                                                                      .getJobParameters()
                                                                                      .get("migrationType"));

                    boolean debug = Boolean.parseBoolean(debugParam);

                    loggingService.setDebugEnabled(debug);
                    loggingService.initializeLogFile(migrationType);
                    loggingService.logInfo("Job started with debug mode: " + debug);

                    return RepeatStatus.FINISHED;
                }, transactionManager
            )
            .build();
    }

    public <T> Step createReadStep(
        String stepName,
        Resource filePath,
        String[] fieldNames,
        Class<T> targetClass,
        boolean writeToCsv,
        boolean dryRun
    ) {
        FlatFileItemReader<T> reader = createCsvReader(filePath, fieldNames, targetClass);
        ItemWriter<MigratedItemGroup> writer = dryRun ? noOpWriter() : (writeToCsv ? itemWriter : noOpWriter());

        return new StepBuilder(stepName, jobRepository)
            .<T, MigratedItemGroup>chunk(BatchConfiguration.CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(itemProcessor)
            .writer(writer)
            .faultTolerant()
            .skipLimit(BatchConfiguration.SKIP_LIMIT)
            .skip(Exception.class)
            .build();
    }

    public <T> FlatFileItemReader<T> createCsvReader(
        Resource inputFile,
        String[] fieldNames,
        Class<T> targetClass
    ) {
        try {
            return CSVReader.createReader(inputFile, fieldNames, targetClass);
        } catch (IOException e) {
            loggingService.logError("Failed to create reader for file: {}" + inputFile.getFilename() + e);
            throw new IllegalStateException("Failed to create reader for file: ", e);
        }
    }

    @Bean
    public <T> ItemWriter<T> noOpWriter() {
        return items -> { /*  no-op writer  does nothing */ };
    }

    public boolean getDryRunFlag() {
        return Optional.ofNullable(JobSynchronizationManager.getContext())
            .map(ctx -> ctx.getJobParameters().get("dryRun"))
            .map(Object::toString)
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    @Bean
    @JobScope
    public Step createArchiveListStep() {
        return createReadStep(
            "archiveListDataStep",
            new ClassPathResource(BatchConfiguration.ARCHIVE_LIST_INITAL),
            new String[]{"archive_name", "create_time", "duration", "file_name", "file_size"},
            CSVArchiveListData.class,
            true,
            getDryRunFlag()
        );
    }

    @Bean
    @JobScope
    public Step createDeltaListStep() {
        return createReadStep(
            "deltaDataStep",
            new ClassPathResource(BatchConfiguration.DELTA_RECORDS_CSV),
            new String[]{"archive_name", "create_time", "duration", "file_name", "file_size"},
            CSVArchiveListData.class,
            true,
            getDryRunFlag()
        );
    }
}
