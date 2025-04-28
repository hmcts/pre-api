package uk.gov.hmcts.reform.preapi.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ArchiveMetadataXmlExtractor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.DeltaProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.PreProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.processor.RecordingMetadataProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.MigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CommonStepUtils;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.config.BatchConfig;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.tasks.BatchRobotUserTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@EnableBatchProcessing
@SpringBootTest(classes = { BatchConfiguration.class, BatchConfig.class })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class BatchConfigurationTest {
    @MockitoBean
    private JobRepository jobRepository;

    @MockitoBean
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private PreProcessor preProcessor;

    @MockitoBean
    private RecordingMetadataProcessor recordingMetadataProcessor;

    @MockitoBean
    private Processor itemProcessor;

    @MockitoBean
    private MigrationWriter writer;

    @MockitoBean
    private MigrationTrackerService migrationTrackerService;

    @MockitoBean
    private BatchRobotUserTask batchRobotUserTask;

    @MockitoBean
    private ArchiveMetadataXmlExtractor archiveMetadataXmlExtractor;

    @MockitoBean
    private DeltaProcessor deltaProcessor;

    @MockitoBean
    private CaseService caseService;

    @MockitoBean
    private LoggingService loggingService;

    @MockitoBean
    private CoreStepsConfig coreStepsConfig;

    @MockitoBean
    private CommonStepUtils commonStepUtils;

    @Autowired
    private BatchConfiguration batchConfiguration;

    @Test
    @DisplayName("Should properly create Delta Processing Step")
    void shouldCreateDeltaProcessingStep() throws Exception {
        Step step = batchConfiguration.createDeltaProcessingStep();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("deltaProcessingStep");

        TaskletStep taskletStep = (TaskletStep) step;
        Tasklet tasklet = taskletStep.getTasklet();
        assertThat(tasklet).isNotNull();

        doNothing().when(deltaProcessor).processDelta(anyString(), anyString(), anyString());

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));
        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(deltaProcessor, times(1))
            .processDelta(
                eq("src/main/resources/batch/Archive_List_initial.csv"),
                eq("src/main/resources/batch/Archive_List_updated.csv"),
                eq("src/main/resources/batch/Archive_List_delta.csv")
            );
    }

    @Test
    @DisplayName("Should properly create PreProcess Step")
    void shouldCreatePreProcessStep() throws Exception {
        Step step = batchConfiguration.createPreProcessStep();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("preProcessStep");

        TaskletStep taskletStep = (TaskletStep) step;
        Tasklet tasklet = taskletStep.getTasklet();
        assertThat(tasklet).isNotNull();

        doNothing().when(preProcessor).initialize();

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));
        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(preProcessor, times(1)).initialize();
    }

    @Test
    @DisplayName("Should properly create Write To CSV Step")
    void shouldCreateWriteToCSVStep() throws Exception {
        Step step = batchConfiguration.createWriteToCSVStep();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("writeToCSVStep");

        TaskletStep taskletStep = (TaskletStep) step;
        Tasklet tasklet = taskletStep.getTasklet();
        assertThat(tasklet).isNotNull();

        doNothing().when(migrationTrackerService).writeAllToCsv();

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));
        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(migrationTrackerService, times(1)).writeAllToCsv();
    }

    @Test
    @DisplayName("Should properly create Robot User Sign-In Step")
    void shouldCreateRobotUserSignInStep() throws Exception {
        Step step = batchConfiguration.createRobotUserSignInStep();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("signInRobotUserStep");

        TaskletStep taskletStep = (TaskletStep) step;
        Tasklet tasklet = taskletStep.getTasklet();
        assertThat(tasklet).isNotNull();

        doNothing().when(batchRobotUserTask).signIn();

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), mock(ChunkContext.class));
        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(batchRobotUserTask, times(1)).signIn();
    }

    @Test
    @DisplayName("Should properly create PreProcess Metadata Step")
    void shouldCreatePreProcessMetadataStep() throws Exception {
        Step step = batchConfiguration.createPreProcessMetadataStep();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("preProcessMetadataStep");

        TaskletStep taskletStep = (TaskletStep) step;
        Tasklet tasklet = taskletStep.getTasklet();
        assertThat(tasklet).isNotNull();

        doNothing().when(recordingMetadataProcessor).processRecording(any());
        ChunkContext mockChunkContext = mock(ChunkContext.class);
        when(mockChunkContext.getStepContext()).thenReturn(mock(StepContext.class));

        RepeatStatus result = tasklet.execute(mock(StepContribution.class), mockChunkContext);
        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(recordingMetadataProcessor, atLeastOnce()).processRecording(any());
    }

    @Test
    @DisplayName("Should properly create Delta Processing Decider for DELTA")
    void shouldCreateDeltaProcessingDeciderWithDeltaMigrationType() {
        JobExecutionDecider decider = batchConfiguration.deltaProcessingDecider();
        JobExecution jobExecution = mock(JobExecution.class);
        StepExecution stepExecution = new StepExecution("testStep", jobExecution);
        JobParameters jobParams = mock(JobParameters.class);
        when(jobExecution.getJobParameters()).thenReturn(jobParams);
        when(jobParams.getString("migrationType")).thenReturn("DELTA");

        FlowExecutionStatus status = decider.decide(jobExecution, stepExecution);
        assertThat(status).isNotNull();
        assertThat(status.getName()).isEqualTo("DELTA");
    }

    @Test
    @DisplayName("Should properly create Delta Processing Decider for FULL")
    void shouldCreateDeltaProcessingDeciderWithFullMigrationType() {
        JobExecutionDecider decider = batchConfiguration.deltaProcessingDecider();
        JobExecution jobExecution = mock(JobExecution.class);
        StepExecution stepExecution = new StepExecution("testStep", jobExecution);
        JobParameters jobParams = mock(JobParameters.class);
        when(jobExecution.getJobParameters()).thenReturn(jobParams);
        when(jobParams.getString("migrationType")).thenReturn("FULL");

        FlowExecutionStatus status = decider.decide(jobExecution, stepExecution);
        assertThat(status).isNotNull();
        assertThat(status.getName()).isEqualTo("FULL");
    }

    @Test
    @DisplayName("Should properly create File Availability Decider")
    void shouldCreateFileAvailabilityDeciderWhenFilesExist() {
        JobExecutionDecider decider = batchConfiguration.fileAvailabilityDecider();
        Resource sites = mock(Resource.class);
        Resource channelReport = mock(Resource.class);
        Resource archiveList = mock(Resource.class);
        when(sites.exists()).thenReturn(true);
        when(channelReport.exists()).thenReturn(true);
        when(archiveList.exists()).thenReturn(true);

        FlowExecutionStatus status = decider.decide(mock(JobExecution.class), null);
        assertThat(status).isNotNull();
        assertThat(status.getName()).isEqualTo("COMPLETED");
    }
}
