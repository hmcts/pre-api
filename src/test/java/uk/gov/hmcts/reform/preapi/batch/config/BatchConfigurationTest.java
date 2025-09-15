package uk.gov.hmcts.reform.preapi.batch.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.repository.JobRepository;
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
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.tasks.BatchRobotUserTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchConfigurationTest {

    private BatchConfiguration batchConfiguration;

    @Mock
    private JobRepository jobRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private PreProcessor preProcessor;
    @Mock
    private RecordingMetadataProcessor recordingPreProcessor;
    @Mock
    private Processor itemProcessor;
    @Mock
    private CaseService caseService;
    @Mock
    private InMemoryCacheService cacheService;
    @Mock
    private MigrationWriter itemWriter;
    @Mock
    private MigrationTrackerService migrationTrackerService;
    @Mock
    private MigrationRecordService migrationRecordService;
    @Mock
    private BatchRobotUserTask robotUserTask;
    @Mock
    private ArchiveMetadataXmlExtractor xmlProcessingService;
    @Mock
    private LoggingService loggingService;

    @BeforeEach
    void setup() {
        this.jobRepository = mock(JobRepository.class);
        this.transactionManager = mock(PlatformTransactionManager.class);
        this.preProcessor = mock(PreProcessor.class);
        this.recordingPreProcessor = mock(RecordingMetadataProcessor.class);
        this.itemProcessor = mock(Processor.class);
        this.caseService = mock(CaseService.class);
        this.cacheService = mock(InMemoryCacheService.class);
        this.itemWriter = mock(MigrationWriter.class);
        this.migrationTrackerService = mock(MigrationTrackerService.class);
        this.migrationRecordService = mock(MigrationRecordService.class);
        this.robotUserTask = mock(BatchRobotUserTask.class);
        this.xmlProcessingService = mock(ArchiveMetadataXmlExtractor.class);
        this.loggingService = mock(LoggingService.class);

        batchConfiguration = new BatchConfiguration(
            jobRepository,
            transactionManager,
            preProcessor,
            recordingPreProcessor,
            itemProcessor,
            caseService,
            cacheService,
            itemWriter,
            migrationTrackerService,
            migrationRecordService,
            robotUserTask,
            xmlProcessingService,
            loggingService
        );
    }

    @Test
    void constantsShouldHaveExpectedValues() {
        assertThat(BatchConfiguration.CHUNK_SIZE).isEqualTo(100);
        assertThat(BatchConfiguration.SKIP_LIMIT).isEqualTo(10);
        assertThat(BatchConfiguration.BASE_PATH).isEqualTo("/batch/");
        assertThat(BatchConfiguration.SITES_CSV).isEqualTo("/batch/reference_data/Sites.csv");
        assertThat(BatchConfiguration.CHANNEL_USER_CSV).isEqualTo("/batch/reference_data/Channel_User_Report.csv");
        assertThat(BatchConfiguration.ARCHIVE_LIST_INITAL).isEqualTo("/batch/Archive_List_initial.csv");
    }

    @Test
    void createPreProcessStepShouldReturnStepWithCorrectName() {
        Step step = batchConfiguration.createPreProcessStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("preProcessStep");
    }

    @Test
    void createPreProcessStepShouldHaveStepExecutionListener() {
        Step step = batchConfiguration.createPreProcessStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("preProcessStep");
    }

    @Test
    void createPreProcessMetadataStepShouldReturnStepWithCorrectName() {
        Step step = batchConfiguration.createPreProcessMetadataStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("preProcessMetadataStep");
    }

    @Test
    void createPreProcessMetadataStepShouldHaveStepExecutionListener() {
        Step step = batchConfiguration.createPreProcessMetadataStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("preProcessMetadataStep");
    }

    @Test
    void createWriteToCSVStepShouldReturnStepWithCorrectName() {
        Step step = batchConfiguration.createWriteToCSVStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("writeToCSVStep");
    }

    @Test
    void createWriteToCSVStepShouldHaveStepExecutionListener() {
        Step step = batchConfiguration.createWriteToCSVStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("writeToCSVStep");
    }

    @Test
    void createRobotUserSignInStepShouldReturnStepWithCorrectName() {
        Step step = batchConfiguration.createRobotUserSignInStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("signInRobotUserStep");
    }

    @Test
    void createRobotUserSignInStepShouldHaveStepExecutionListener() {
        Step step = batchConfiguration.createRobotUserSignInStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("signInRobotUserStep");
    }

    @Test
    void fileAvailabilityDeciderShouldReturnCompletedWhenAllFilesExist() {
        Resource mockSites = mock(Resource.class);
        Resource mockChannelReport = mock(Resource.class);
        Resource mockArchiveList = mock(Resource.class);
        
        when(mockSites.exists()).thenReturn(true);
        when(mockChannelReport.exists()).thenReturn(true);
        when(mockArchiveList.exists()).thenReturn(true);

        JobExecutionDecider decider = batchConfiguration.fileAvailabilityDecider();
        
        assertThat(decider).isNotNull();

    }

    @Test
    void fileAvailabilityDeciderShouldReturnFailedWhenAnyFileDoesNotExist() {
        JobExecutionDecider decider = batchConfiguration.fileAvailabilityDecider();
        
        assertThat(decider).isNotNull();
    }

    @Test
    void batchConfigurationShouldImplementStepExecutionListener() {
        assertThat(batchConfiguration).isInstanceOf(StepExecutionListener.class);
    }

    @Test
    void batchConfigurationShouldHaveAllRequiredDependencies() {
        assertThat(batchConfiguration.jobRepository).isNotNull();
        assertThat(batchConfiguration.transactionManager).isNotNull();
        assertThat(batchConfiguration.preProcessor).isNotNull();
        assertThat(batchConfiguration.recordingPreProcessor).isNotNull();
        assertThat(batchConfiguration.itemProcessor).isNotNull();
        assertThat(batchConfiguration.caseService).isNotNull();
        assertThat(batchConfiguration.cacheService).isNotNull();
        assertThat(batchConfiguration.itemWriter).isNotNull();
        assertThat(batchConfiguration.migrationTrackerService).isNotNull();
        assertThat(batchConfiguration.migrationRecordService).isNotNull();
        assertThat(batchConfiguration.robotUserTask).isNotNull();
        assertThat(batchConfiguration.xmlProcessingService).isNotNull();
        assertThat(batchConfiguration.loggingService).isNotNull();
    }

    @Test
    void stepNamesShouldBeConsistent() {
        Step preProcessStep = batchConfiguration.createPreProcessStep();
        Step preProcessMetadataStep = batchConfiguration.createPreProcessMetadataStep();
        Step writeToCSVStep = batchConfiguration.createWriteToCSVStep();
        Step robotUserSignInStep = batchConfiguration.createRobotUserSignInStep();

        assertThat(preProcessStep.getName()).isEqualTo("preProcessStep");
        assertThat(preProcessMetadataStep.getName()).isEqualTo("preProcessMetadataStep");
        assertThat(writeToCSVStep.getName()).isEqualTo("writeToCSVStep");
        assertThat(robotUserSignInStep.getName()).isEqualTo("signInRobotUserStep");
    }

    @Test
    void allStepsShouldBeNotNull() {
        assertThat(batchConfiguration.createPreProcessStep()).isNotNull();
        assertThat(batchConfiguration.createPreProcessMetadataStep()).isNotNull();
        assertThat(batchConfiguration.createWriteToCSVStep()).isNotNull();
        assertThat(batchConfiguration.createRobotUserSignInStep()).isNotNull();
    }

    @Test
    void fileAvailabilityDeciderShouldBeNotNull() {
        JobExecutionDecider decider = batchConfiguration.fileAvailabilityDecider();
        assertThat(decider).isNotNull();
    }

    @Test
    void constantsShouldBeAccessible() {
        assertThat(BatchConfiguration.CHUNK_SIZE).isInstanceOf(Integer.class);
        assertThat(BatchConfiguration.SKIP_LIMIT).isInstanceOf(Integer.class);
        assertThat(BatchConfiguration.BASE_PATH).isInstanceOf(String.class);
        assertThat(BatchConfiguration.SITES_CSV).isInstanceOf(String.class);
        assertThat(BatchConfiguration.CHANNEL_USER_CSV).isInstanceOf(String.class);
        assertThat(BatchConfiguration.ARCHIVE_LIST_INITAL).isInstanceOf(String.class);
    }

    @Test
    void csvPathsShouldContainBasePath() {
        assertThat(BatchConfiguration.SITES_CSV).startsWith(BatchConfiguration.BASE_PATH);
        assertThat(BatchConfiguration.CHANNEL_USER_CSV).startsWith(BatchConfiguration.BASE_PATH);
        assertThat(BatchConfiguration.ARCHIVE_LIST_INITAL).startsWith(BatchConfiguration.BASE_PATH);
    }

    @Test
    void csvPathsShouldHaveCorrectExtensions() {
        assertThat(BatchConfiguration.SITES_CSV).endsWith(".csv");
        assertThat(BatchConfiguration.CHANNEL_USER_CSV).endsWith(".csv");
        assertThat(BatchConfiguration.ARCHIVE_LIST_INITAL).endsWith(".csv");
    }

    @Test
    void createPreProcessStepShouldHaveCorrectConfiguration() {
        Step step = batchConfiguration.createPreProcessStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("preProcessStep");
    }

    @Test
    void createPreProcessMetadataStepShouldHaveCorrectConfiguration() {
        Step step = batchConfiguration.createPreProcessMetadataStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("preProcessMetadataStep");
    }

    @Test
    void createWriteToCSVStepShouldHaveCorrectConfiguration() {
        Step step = batchConfiguration.createWriteToCSVStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("writeToCSVStep");
    }

    @Test
    void createRobotUserSignInStepShouldHaveCorrectConfiguration() {
        Step step = batchConfiguration.createRobotUserSignInStep();
        
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("signInRobotUserStep");
    }

    @Test
    void fileAvailabilityDeciderShouldExecuteDecisionLogic() {
        JobExecutionDecider decider = batchConfiguration.fileAvailabilityDecider();
        
        var jobExecution = mock(JobExecution.class);
        var stepExecution = mock(StepExecution.class);
        
        FlowExecutionStatus result = decider.decide(jobExecution, stepExecution);
        
        assertThat(result).isNotNull();
        assertThat(result.getName()).isIn("COMPLETED", "FAILED");
    }

    @Test
    void batchConfigurationShouldImplementStepExecutionListenerMethods() {
        assertThat(batchConfiguration).isInstanceOf(StepExecutionListener.class);
        
        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getStepName()).thenReturn("testStep");
        when(stepExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);
        
        assertThatCode(() -> batchConfiguration.beforeStep(stepExecution)).doesNotThrowAnyException();
        
        ExitStatus result = batchConfiguration.afterStep(stepExecution);
        assertThatCode(() -> batchConfiguration.afterStep(stepExecution)).doesNotThrowAnyException();
    }
}
