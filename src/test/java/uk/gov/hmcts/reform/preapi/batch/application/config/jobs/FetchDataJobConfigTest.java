package uk.gov.hmcts.reform.preapi.batch.application.config.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.ArchiveMetadataXmlExtractor;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.jobs.FetchDataJobConfig;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FetchDataJobConfigTest {

    private JobRepository jobRepository;
    private PlatformTransactionManager transactionManager;
    private ArchiveMetadataXmlExtractor xmlProcessingService;
    private MigrationRecordService migrationRecordService;
    private CoreStepsConfig coreSteps;
    private LoggingService loggingService;
    private FetchDataJobConfig config;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        xmlProcessingService = mock(ArchiveMetadataXmlExtractor.class);
        migrationRecordService = mock(MigrationRecordService.class);
        coreSteps = mock(CoreStepsConfig.class);
        loggingService = mock(LoggingService.class);

        // Mock the startLogging step
        Step mockStep = mock(Step.class);
        when(coreSteps.startLogging()).thenReturn(mockStep);

        config = new FetchDataJobConfig(
            jobRepository,
            transactionManager,
            coreSteps,
            xmlProcessingService,
            migrationRecordService,
            loggingService
        );
    }

    @Test
    void fetchDataJob_shouldReturnJobBean() {
        Job job = config.fetchDataJob();
        assertNotNull(job);
        assertEquals("fetchDataJob", job.getName());
    }

    @Test
    void createXmlFetchStep_shouldReturnStepBean() {
        Step step = config.createXmlFetchStep();
        assertNotNull(step);
        assertEquals("fetchAndConvertXmlFileStep", step.getName());
    }

    @Test
    void createXmlFetchStep_shouldHandleCsvSourceType() throws Exception {

        // Create job parameters with CSV source type
        var jobParams = new HashMap<String, Object>();
        jobParams.put("sourceType", "csv");

        ChunkContext chunkContext = mock(ChunkContext.class);
        StepContext stepContext = mock(StepContext.class);
        StepExecution stepExecution = mock(StepExecution.class);

        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(jobParams);
        when(stepContext.getStepExecution()).thenReturn(stepExecution);

        // Mock the execution context for the CSV reader
        when(stepExecution.getExecutionContext()).thenReturn(new org.springframework.batch.item.ExecutionContext());

        // Execute the tasklet by extracting it from the step
        Step step = config.createXmlFetchStep();
        var taskletStep = (TaskletStep) step;
        var tasklet = taskletStep.getTasklet();

        var status = tasklet.execute(null, chunkContext);

        assertEquals(RepeatStatus.FINISHED, status);
        verify(loggingService).logInfo(contains("Skipping XML fetch"), eq("Archive_List_initial"));
    }

    @Test
    void createXmlFetchStep_shouldHandleXmlSourceType() throws Exception {

        // Create job parameters with XML source type
        var jobParams = new HashMap<String, Object>();
        jobParams.put("sourceType", "xml");

        ChunkContext chunkContext = mock(ChunkContext.class);
        StepContext stepContext = mock(StepContext.class);

        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(jobParams);

        // Execute the tasklet
        Step step = config.createXmlFetchStep();
        var taskletStep = (TaskletStep) step;
        var tasklet = taskletStep.getTasklet();

        var status = tasklet.execute(null, chunkContext);

        assertEquals(RepeatStatus.FINISHED, status);
        verify(xmlProcessingService).extractAndReportArchiveMetadata(
            eq(FetchDataJobConfig.CONTAINER_NAME),
            eq(FetchDataJobConfig.XML_PREFIX),
            eq(FetchDataJobConfig.FULL_PATH),
            eq("Archive_List_initial")
        );
    }

    @Test
    void createXmlFetchStep_shouldHandleDefaultSourceType() throws Exception {
        Step step = config.createXmlFetchStep();

        // Create job parameters without sourceType (should default to XML)
        var jobParams = new HashMap<String, Object>();

        ChunkContext chunkContext = mock(ChunkContext.class);
        StepContext stepContext = mock(StepContext.class);

        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(jobParams);

        // Execute the tasklet
        var taskletStep = (TaskletStep) step;
        var tasklet = taskletStep.getTasklet();

        var status = tasklet.execute(null, chunkContext);

        assertEquals(RepeatStatus.FINISHED, status);
        verify(xmlProcessingService).extractAndReportArchiveMetadata(
            eq(FetchDataJobConfig.CONTAINER_NAME),
            eq(FetchDataJobConfig.XML_PREFIX),
            eq(FetchDataJobConfig.FULL_PATH),
            eq("Archive_List_initial")
        );
    }
}
