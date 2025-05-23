package uk.gov.hmcts.reform.preapi.batch.config;

import lombok.Cleanup;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ArchiveMetadataXmlExtractor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.DeltaProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.PreProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.RecordingMetadataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.CSVReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.MigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CommonStepUtils;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVArchiveListData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVExemptionListData;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.tasks.BatchRobotUserTask;

import java.util.Objects;
import java.util.Optional;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration implements StepExecutionListener {
    public static final int CHUNK_SIZE = 100;
    public static final int SKIP_LIMIT = 10;
    public static final String FULL_PATH = "src/main/resources/batch";
    public static final String BASE_PATH = "/batch/";
    public static final String SITES_CSV = BASE_PATH + "reference_data/Sites.csv";
    public static final String CHANNEL_USER_CSV = BASE_PATH + "reference_data/Channel_User_Report.csv";
    public static final String ARCHIVE_LIST_INITAL = BASE_PATH + "Archive_List_initial.csv";
    public static final String DELTA_RECORDS_CSV = BASE_PATH + "Archive_List_delta.csv";
    public static final String EXCEMPTIONS_LIST_CSV = BASE_PATH + "Excemption_List.csv";

    public final JobRepository jobRepository;
    public final PlatformTransactionManager transactionManager;
    public final InMemoryCacheService cacheService;
    public final PreProcessor preProcessor;
    public final RecordingMetadataProcessor recordingPreProcessor;
    public final Processor itemProcessor;
    public final MigrationWriter itemWriter;
    public final MigrationTrackerService migrationTrackerService;
    public final BatchRobotUserTask robotUserTask;
    public final ArchiveMetadataXmlExtractor xmlProcessingService;
    public final DeltaProcessor deltaProcessor;
    public final CaseService caseService;
    public LoggingService loggingService;
    private final CoreStepsConfig coreSteps;
    private final CommonStepUtils stepUtils;

    @Autowired
    public BatchConfiguration(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        PreProcessor preProcessor,
        RecordingMetadataProcessor recordingPreProcessor,
        Processor itemProcessor,
        CaseService caseService,
        InMemoryCacheService cacheService,
        MigrationWriter itemWriter,
        MigrationTrackerService migrationTrackerService,
        BatchRobotUserTask robotUserTask,
        ArchiveMetadataXmlExtractor xmlProcessingService,
        LoggingService loggingService,
        DeltaProcessor deltaProcessor,
        CoreStepsConfig coreSteps,
        CommonStepUtils stepUtils
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.preProcessor = preProcessor;
        this.recordingPreProcessor = recordingPreProcessor;
        this.caseService = caseService;
        this.cacheService = cacheService;
        this.itemProcessor = itemProcessor;
        this.itemWriter = itemWriter;
        this.migrationTrackerService = migrationTrackerService;
        this.robotUserTask = robotUserTask;
        this.xmlProcessingService = xmlProcessingService;
        this.loggingService = loggingService;
        this.deltaProcessor = deltaProcessor;
        this.coreSteps = coreSteps;
        this.stepUtils = stepUtils;
    }

    @Bean
    public Step createDeltaProcessingStep() {
        return new StepBuilder("deltaProcessingStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                String deltaFilePath = "src/main/resources/batch/Archive_List_delta.csv";
                deltaProcessor.processDelta(
                    "src/main/resources/batch/Archive_List_initial.csv",
                    "src/main/resources/batch/Archive_List_updated.csv",
                    deltaFilePath
                );
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    @Bean
    @JobScope
    public Step createExemptionListStep() {
        return stepUtils.buildChunkStep(
            "exemptionListDataStep",
            new ClassPathResource(EXCEMPTIONS_LIST_CSV),
            new String[] {
                "archive_name","create_time","duration","court_reference","urn",
                "exhibit_reference","defendant_name","witness_name","recording_version",
                "recording_version_number","file_extension","file_name","file_size","reason","added_by"
            },
            CSVExemptionListData.class,
            coreSteps.getDryRunFlag() ? coreSteps.noOpWriter() : itemWriter,
            jobRepository,
            transactionManager
        );
    }

    @Bean
    public Step createPreProcessStep() {
        return new StepBuilder("preProcessStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    preProcessor.initialize();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
            .build();
    }

    @Bean
    public Step createPreProcessMetadataStep() {
        return new StepBuilder("preProcessMetadataStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    String migrationType = Optional.ofNullable((String) chunkContext.getStepContext()
                                    .getJobParameters().get("migrationType")).orElse("FULL");

                    String filePath = "FULL".equalsIgnoreCase(migrationType)
                        ? ARCHIVE_LIST_INITAL
                        : DELTA_RECORDS_CSV;

                    Resource resource = new ClassPathResource(filePath);
                    String[] fieldNames = {"archive_name", "create_time", "duration", "file_name", "file_size"};

                    @Cleanup FlatFileItemReader<CSVArchiveListData> reader = CSVReader.createReader(
                        resource, fieldNames, CSVArchiveListData.class
                    );
                    reader.open(new ExecutionContext());

                    CSVArchiveListData item;
                    while ((item = reader.read()) != null) {
                        recordingPreProcessor.processRecording(item);
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
            .build();
    }

    @Bean
    public Step createWriteToCSVStep() {
        return new StepBuilder("writeToCSVStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    migrationTrackerService.writeAllToCsv();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
            .build();
    }

    @Bean
    public Step createRobotUserSignInStep() {
        return new StepBuilder("signInRobotUserStep", jobRepository)
            .tasklet(
                (contribution, chunkContext) -> {
                    robotUserTask.signIn();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
            .build();
    }

    // =========================
    // Utility and Helper Functions
    // =========================

    @Bean
    public JobExecutionDecider deltaProcessingDecider() {
        return (jobExecution, stepExecution) -> {
            var migrationType = MigrationType.fromString(
                Objects.requireNonNull(jobExecution.getJobParameters().getString("migrationType"))
            );

            if (migrationType.equals(MigrationType.DELTA)) {
                return new FlowExecutionStatus("DELTA");
            } else {
                return new FlowExecutionStatus("FULL");
            }
        };
    }

    public JobExecutionDecider fileAvailabilityDecider() {
        return (jobExecution, stepExecution) -> {
            Resource sites = new ClassPathResource(SITES_CSV);
            Resource channelReport = new ClassPathResource(CHANNEL_USER_CSV);
            Resource archiveList = new ClassPathResource(ARCHIVE_LIST_INITAL);

            return sites.exists() && channelReport.exists() && archiveList.exists()
                ? new FlowExecutionStatus("COMPLETED")
                : new FlowExecutionStatus("FAILED");
        };
    }
}
