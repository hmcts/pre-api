package uk.gov.hmcts.reform.preapi.config.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.processor.PreProcessor;
import uk.gov.hmcts.reform.preapi.batch.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.processor.RecordingMetadataProcessor;
import uk.gov.hmcts.reform.preapi.batch.processor.ReferenceDataProcessor;
import uk.gov.hmcts.reform.preapi.batch.processor.XmlProcessingService;
import uk.gov.hmcts.reform.preapi.batch.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.writer.Writer;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVChannelData;
import uk.gov.hmcts.reform.preapi.entities.batch.CSVSitesData;
import uk.gov.hmcts.reform.preapi.entities.batch.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.batch.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.tasks.RobotUserTaskImpl;

import java.io.File;
import java.io.IOException;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration implements StepExecutionListener {
    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    private static final int CHUNK_SIZE = 10;
    private static final int SKIP_LIMIT = 10;

    private static final class BatchResources {
        static final String BASE_PATH = "batch/";
        static final String SITES_CSV = BASE_PATH + "Sites.csv";
        static final String CHANNEL_USER_CSV = BASE_PATH + "Channel_User_Report.csv";
        static final String ARCHIVE_LIST_CSV = BASE_PATH + "Archive_List.csv";
        
        private BatchResources() {}
    }

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CSVReader csvReader;
    private final PreProcessor preProcessor;
    private final RecordingMetadataProcessor recordingPreProcessor;
    private final ReferenceDataProcessor referenceDataProcessor;
    private final Processor itemProcessor;
    private final Writer itemWriter;
    private final MigrationTrackerService migrationTrackerService;
    private final CaseService caseService;
    private final RobotUserTaskImpl robotUserTask;
    private final XmlProcessingService xmlProcessingService;

    
    @Autowired
    public BatchConfiguration(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        CSVReader csvReader,
        PreProcessor preProcessor,
        RecordingMetadataProcessor recordingPreProcessor,
        ReferenceDataProcessor referenceDataProcessor,
        Processor itemProcessor,
        CaseService caseService,
        Writer itemWriter,
        MigrationTrackerService migrationTrackerService,
        RobotUserTaskImpl robotUserTask,
        XmlProcessingService xmlProcessingService
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.csvReader = csvReader;
        this.preProcessor = preProcessor;
        this.recordingPreProcessor =  recordingPreProcessor;
        this.referenceDataProcessor = referenceDataProcessor;
        this.itemProcessor = itemProcessor;
        this.caseService = caseService;
        this.itemWriter = itemWriter;
        this.migrationTrackerService = migrationTrackerService;
        this.robotUserTask = robotUserTask;
        this.xmlProcessingService = xmlProcessingService;
    }

    // =========================
    // Job Definitions
    // =========================

    @Bean
    @Qualifier("fetchXmlJob")
    public Job fetchXmlJob() {
        return new JobBuilder("fetchXmlJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(createXmlFetchStep()) 
            .build();
    }

    @Bean
    @Qualifier("importCsvJob")
    public Job processCSVJob() {
        return new JobBuilder("importCsvJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(fileAvailabilityDecider())
            .on("FAILED").end()
            .on("COMPLETED")
            .to(createSitesDataStep())
            .next(createChannelUserStep())
            .next(createRobotUserSignInStep())
            .next(createPreProcessStep())
            .next(createPreProcessMetadataStep())
            .next(createArchiveListStep())
            .next(createMarkCasesClosedStep()) 
            .next(createWriteToCSVStep())
            .end()
            .build();
    }

    // =========================
    // Step Definitions
    // =========================
    private Step createSitesDataStep() {
        return createReadStep(
            "sitesDataStep",
            new ClassPathResource(BatchResources.SITES_CSV),
            new String[]{"site_reference", "site_name", "location", "court_name"},
            CSVSitesData.class,
            false
        );
    }

    private Step createChannelUserStep() {
        return createReadStep(
            "channelUserStep",
            new ClassPathResource(BatchResources.CHANNEL_USER_CSV),
            new String[]{"channel_name", "channel_user", "channel_user_email"},
            CSVChannelData.class,
            false
        );
    }

    private Step createArchiveListStep() {
        return createReadStep(
            "archiveListDataStep",
            new ClassPathResource(BatchResources.ARCHIVE_LIST_CSV),
            new String[]{"archive_name", "create_time", "duration", "file_name", "file_size"},
            CSVArchiveListData.class,
            true
        );
    }

    @Bean
    @JobScope
    protected Step createXmlFetchStep() {
        return new StepBuilder("fetchAndConvertXmlFileStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String containerName = "pre-vodafone-spike"; 
                    String outputDir = "src/main/resources/batch"; 
                    xmlProcessingService.processXmlAndWriteCsv(containerName, outputDir);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    protected Step createPreProcessStep() {
        return new StepBuilder("preProcessStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    preProcessor.initialize();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    protected Step createPreProcessMetadataStep() {
        return new StepBuilder("preProcessMetadataStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                Resource resource = new ClassPathResource("batch/Archive_List.csv");
                String[] fieldNames = {"archive_name", "create_time", "duration", "file_name", "file_size"};

                FlatFileItemReader<CSVArchiveListData> reader = csvReader.createReader(
                    resource, fieldNames, CSVArchiveListData.class
                );

                reader.open(new ExecutionContext());

                CSVArchiveListData item;
                while ((item = reader.read()) != null) {
                    recordingPreProcessor.processRecording(item); 
                }

                reader.close();

                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    protected Step createWriteToCSVStep() {
        return new StepBuilder("writeToCSVStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    migrationTrackerService.writeAllToCsv();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    protected Step createRobotUserSignInStep() {
        return new StepBuilder("signInRobotUserStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                robotUserTask.signIn();
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    protected Step createMarkCasesClosedStep() {
        return new StepBuilder("markCasesClosedStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                var validCaseReferences = referenceDataProcessor.fetchChannelUserDataKeys();
                validCaseReferences.forEach(caseReference -> {
                    var foundCase = caseService
                            .searchBy(caseReference, null, false, Pageable.unpaged())
                            .toList();
                    // logger.info("Case reference '{}' returned: {}", caseReference, foundCase);
                });
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }


    // =========================
    // Utility and Helper Functions
    // =========================
    protected JobExecutionDecider fileAvailabilityDecider() {
        return (jobExecution, stepExecution) -> {
            File sites = new File("src/main/resources/batch/Sites.csv");
            File channelReport = new File("src/main/resources/batch/Channel_User_Report.csv");
            File archiveList = new File("src/main/resources/batch/Archive_List.csv");

            if (sites.exists() && channelReport.exists() && archiveList.exists()) {
                return new FlowExecutionStatus("COMPLETED");
            } else {
                return new FlowExecutionStatus("FAILED");
            }
        };
    }

    protected <T> Step createReadStep(
        String stepName, 
        Resource filePath, 
        String[] fieldNames, 
        Class<T> targetClass, 
        boolean writeToCsv
    ) {
        FlatFileItemReader<T> reader = createCsvReader(filePath, fieldNames, targetClass);
        return new StepBuilder(stepName, jobRepository)
            .<T, MigratedItemGroup>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(itemProcessor)
            .writer(writeToCsv ? itemWriter : noOpWriter())
            .faultTolerant()
            .skipLimit(SKIP_LIMIT)
            .skip(Exception.class)
            .build();
    }

        
    private <T> FlatFileItemReader<T> createCsvReader(
        Resource inputFile, 
        String[] fieldNames, 
        Class<T> targetClass
    ) {
        try {
            return csvReader.createReader(inputFile, fieldNames, targetClass);
        } catch (IOException e) {
            logger.error("Failed to create reader for file: {}", inputFile.getFilename(), e);
            throw new IllegalStateException("Failed to create reader for file: ", e);
        }
    }

    @Bean
    public <T> ItemWriter<T> noOpWriter() {
        return items -> { /*  no-op writer  does nothing */ };
    }

}
