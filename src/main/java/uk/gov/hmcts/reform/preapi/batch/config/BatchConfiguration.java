package uk.gov.hmcts.reform.preapi.batch.config;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ArchiveMetadataXmlExtractor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.PreProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.RecordingMetadataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.MigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.tasks.BatchRobotUserTask;

import java.util.List;

@Configuration
@EnableBatchProcessing
public class BatchConfiguration implements StepExecutionListener {
    public static final int CHUNK_SIZE = 100;
    public static final int SKIP_LIMIT = 10;
    public static final String BASE_PATH = "/batch/";
    public static final String SITES_CSV = BASE_PATH + "reference_data/Sites.csv";
    public static final String CHANNEL_USER_CSV = BASE_PATH + "reference_data/Channel_User_Report.csv";
    public static final String ARCHIVE_LIST_INITAL = BASE_PATH + "Archive_List_initial.csv";

    public final JobRepository jobRepository;
    public final PlatformTransactionManager transactionManager;
    public final InMemoryCacheService cacheService;
    public final PreProcessor preProcessor;
    public final RecordingMetadataProcessor recordingPreProcessor;
    public final Processor itemProcessor;
    public final MigrationWriter itemWriter;
    public final MigrationTrackerService migrationTrackerService;
    public final MigrationRecordService migrationRecordService;
    public final BatchRobotUserTask robotUserTask;
    public final ArchiveMetadataXmlExtractor xmlProcessingService;
    public final CaseService caseService;
    public LoggingService loggingService;

    @Autowired
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public BatchConfiguration(final JobRepository jobRepository,
                              final PlatformTransactionManager transactionManager,
                              final PreProcessor preProcessor,
                              final RecordingMetadataProcessor recordingPreProcessor,
                              final Processor itemProcessor,
                              final CaseService caseService,
                              final InMemoryCacheService cacheService,
                              final MigrationWriter itemWriter,
                              final MigrationTrackerService migrationTrackerService,
                              final MigrationRecordService migrationRecordService,
                              final BatchRobotUserTask robotUserTask,
                              final ArchiveMetadataXmlExtractor xmlProcessingService,
                              final LoggingService loggingService) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.preProcessor = preProcessor;
        this.recordingPreProcessor = recordingPreProcessor;
        this.caseService = caseService;
        this.cacheService = cacheService;
        this.itemProcessor = itemProcessor;
        this.itemWriter = itemWriter;
        this.migrationTrackerService = migrationTrackerService;
        this.migrationRecordService = migrationRecordService;
        this.robotUserTask = robotUserTask;
        this.xmlProcessingService = xmlProcessingService;
        this.loggingService = loggingService;
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
                    List<MigrationRecord> pendingRecords = migrationRecordService.getPendingMigrationRecords();

                    if (pendingRecords.isEmpty()) {
                        loggingService.logInfo("No pending migration records to pre-process.");
                        return RepeatStatus.FINISHED;
                    }

                    for (MigrationRecord record : pendingRecords) {
                        recordingPreProcessor.processRecording(record);
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
                    migrationTrackerService.startNewReportRun();
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
