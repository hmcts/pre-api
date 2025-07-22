package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ArchiveMetadataXmlExtractor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.MigrationType;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;

@Configuration
public class FetchDataJobConfig {

    // public static final String CONTAINER_NAME = "pre-vodafone-spike";
    // public static final String CONTAINER_NAME = "piotr";
    // public static final String XML_PREFIX = "NFS-copy"; 
    public static final String CONTAINER_NAME = "poc";
    public static final String XML_PREFIX = "MS06"; 
    public static final String FULL_PATH = "src/main/resources/batch";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ArchiveMetadataXmlExtractor xmlProcessingService;
    private final MigrationRecordService migrationRecordService;
    private final CoreStepsConfig coreSteps;
    private final LoggingService loggingService;


    public FetchDataJobConfig(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        CoreStepsConfig coreSteps,
        ArchiveMetadataXmlExtractor xmlProcessingService,
        MigrationRecordService migrationRecordService,
        LoggingService loggingService
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.coreSteps = coreSteps;
        this.xmlProcessingService = xmlProcessingService;
        this.migrationRecordService = migrationRecordService;
        this.loggingService = loggingService;
    }

    @Bean
    public Job fetchDataJob() {
        return new JobBuilder("fetchDataJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(coreSteps.startLogging())
            .next(createXmlFetchStep())
            .build();
    }

    @Bean
    public Step createXmlFetchStep() {
        return new StepBuilder("fetchAndConvertXmlFileStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                var jobParams = chunkContext.getStepContext().getJobParameters();

                MigrationType migrationType = MigrationType.fromString((String) jobParams.get("migrationType"));
                String outputFileName = migrationType.equals(MigrationType.DELTA)
                    ? "Archive_List_updated"
                    : "Archive_List_initial";

                String sourceType = (String) jobParams.getOrDefault("sourceType", "xml");
                if ("csv".equalsIgnoreCase(sourceType)) {
                    loggingService.logInfo(
                        "FetchDataJob - Skipping XML fetch. Using pre-generated CSV file: %s.csv%n", outputFileName);
                    var resource = new FileSystemResource(FULL_PATH + "/" + outputFileName + ".csv");

                    FlatFileItemReader<CSVArchiveListData> reader = CSVReader.createReader(
                        resource,
                        new String[]{"archive_id", "archive_name", "create_time", "duration", "file_name", "file_size"},
                        CSVArchiveListData.class
                    );

                    reader.open(chunkContext.getStepContext().getStepExecution().getExecutionContext());

                    CSVArchiveListData record;
                    int count = 0;
                    while ((record = reader.read()) != null) {
                        migrationRecordService.insertPending(record);
                        count++;
                    }

                    reader.close();
                    loggingService.logInfo("FetchDataJob - Inserted %d pending records from CSV.", count);
                } else {
                    loggingService.logInfo("FetchDataJob - Fetching XMLs from Azure container '%s' with prefix '%s'%n", 
                        CONTAINER_NAME, XML_PREFIX);
                    xmlProcessingService.extractAndReportArchiveMetadata(
                        CONTAINER_NAME, XML_PREFIX, FULL_PATH, outputFileName);
                }
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }
}
