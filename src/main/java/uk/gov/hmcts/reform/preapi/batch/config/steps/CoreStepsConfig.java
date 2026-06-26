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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.MigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;

import java.io.IOException;
import java.util.Optional;

@Configuration
public class CoreStepsConfig {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final Processor itemProcessor;
    private final MigrationTrackerService migrationTrackerService;
    private final MigrationWriter itemWriter;
    private final LoggingService loggingService;
    private final AzureVodafoneStorageService azureVodafoneStorageService;

    @Value("${azure.vodafoneStorage.csvContainer}")
    private String containerName;

    @Value("${USE_LOCAL_CSV:false}")
    private boolean useLocalCsv;

    private static final String CHANNEL_USER_CSV_BLOB_PATH = "Channel_User_Report.csv";
    private static final String CHANNEL_USER_CSV_LOCAL_PATH =
        "src/main/resources/batch/reference_data/Channel_User_Report.csv";

    public CoreStepsConfig(final JobRepository jobRepository,
                           final PlatformTransactionManager transactionManager,
                           final Processor itemProcessor,
                           final MigrationTrackerService migrationTrackerService,
                           final MigrationWriter itemWriter,
                           final LoggingService loggingService,
                           final AzureVodafoneStorageService azureVodafoneStorageService) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.itemProcessor = itemProcessor;
        this.migrationTrackerService = migrationTrackerService;
        this.itemWriter = itemWriter;
        this.loggingService = loggingService;
        this.azureVodafoneStorageService = azureVodafoneStorageService;
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
        try {
            Resource channelUserResource = getChannelUserCsvResource();
            return createReadStep(
                "channelUserStep",
                channelUserResource,
                new String[]{"channel_name", "channel_user", "channel_user_email"},
                CSVChannelData.class,
                false,
                isDryRun()
            );
        } catch (IOException e) {
            loggingService.logError("Failed to load Channel User CSV: %s", e.getMessage());
            throw new IllegalStateException("Failed to load Channel User CSV", e);
        }
    }

    private Resource getChannelUserCsvResource() throws IOException {
        if (useLocalCsv) {
            loggingService.logInfo("Reading Channel User CSV from local file: {}", CHANNEL_USER_CSV_LOCAL_PATH);
            Resource resource = new FileSystemResource(CHANNEL_USER_CSV_LOCAL_PATH);

            if (!resource.exists()) {
                resource = new ClassPathResource(BatchConfiguration.CHANNEL_USER_CSV);
            }

            if (!resource.exists()) {
                throw new IOException("Channel User CSV file not found at local path: " + CHANNEL_USER_CSV_LOCAL_PATH);
            }
            return resource;
        } else {
            loggingService.logInfo(
                "Reading Channel User CSV from Azure blob: {}/{}",
                containerName,
                CHANNEL_USER_CSV_BLOB_PATH
            );
            InputStreamResource blobResource = azureVodafoneStorageService
                .fetchSingleXmlBlob(containerName, CHANNEL_USER_CSV_BLOB_PATH);

            if (blobResource == null) {
                // Fallback to classpath if Azure blob not found
                loggingService.logWarning("Channel User CSV not found in Azure, falling back to classpath");
                Resource resource = new ClassPathResource(BatchConfiguration.CHANNEL_USER_CSV);
                if (!resource.exists()) {
                    throw new IOException(
                        "Channel User CSV file not found in Azure: " + containerName + "/" + CHANNEL_USER_CSV_BLOB_PATH
                    );
                }
                return resource;
            }
            return blobResource;
        }
    }

    public Step startLogging() {
        return new StepBuilder("loggingStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    String debugParam = (String) chunkContext.getStepContext()
                        .getJobParameters()
                        .get("debug");

                    boolean debug = Boolean.parseBoolean(debugParam);

                    loggingService.setDebugEnabled(debug);
                    loggingService.initializeLogFile();
                    loggingService.logInfo("Job started with debug mode: " + debug);

                    return RepeatStatus.FINISHED;
                }, transactionManager)
            .build();
    }

    public <T> Step createReadStep(String stepName,
                                   Resource filePath,
                                   String[] fieldNames,
                                   Class<T> targetClass,
                                   boolean writeToCsv,
                                   boolean dryRun) {
        FlatFileItemReader<T> reader = createCsvReader(filePath, fieldNames, targetClass);

        ItemWriter<MigratedItemGroup> writer;
        if (dryRun) {
            writer = chunk -> {
                for (MigratedItemGroup item : chunk) {
                    if (item != null && item.getPassItem() != null) {
                        migrationTrackerService.addMigratedItem(item.getPassItem());
                    }
                }
            };
        } else {
            writer = writeToCsv ? itemWriter : noOpWriter();
        }

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

    public <T> FlatFileItemReader<T> createCsvReader(Resource inputFile, String[] fieldNames, Class<T> targetClass) {
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

    public boolean isDryRun() {
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
            new String[]{"archive_id", "archive_name", "create_time", "duration", "file_name", "file_size"},
            CSVArchiveListData.class,
            true,
            isDryRun()
        );
    }
}
