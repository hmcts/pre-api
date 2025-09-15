package uk.gov.hmcts.reform.preapi.batch.config.steps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.JobSynchronizationManager;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.MigrationWriter;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreStepsConfigTest {

    private CoreStepsConfig stepsConfig;

    @Mock
    private JobRepository jobRepository;
    @Mock
    private Processor itemProcessor;
    @Mock
    private MigrationTrackerService migrationTrackerService;
    @Mock
    private MigrationWriter itemWriter;
    @Mock
    private LoggingService loggingService;

    @BeforeEach
    void setup() {
        this.jobRepository = mock(JobRepository.class);
        this.itemProcessor = mock(Processor.class);
        this.migrationTrackerService = mock(MigrationTrackerService.class);
        this.itemWriter = mock(MigrationWriter.class);
        this.loggingService = mock(LoggingService.class);

        PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();

        stepsConfig = new CoreStepsConfig(
            jobRepository,
            transactionManager,
            itemProcessor,
            migrationTrackerService,
            itemWriter,
            loggingService
        );
    }

    @Test
    void getDryRunFlagShouldReturnFalseWhenNoContext() {
        JobSynchronizationManager.close();
        assertThat(stepsConfig.getDryRunFlag()).isFalse();
    }


    @Test
    void createArchiveListStepReturnsStep() {
        Step step = stepsConfig.createArchiveListStep();
        assertThat(step).isNotNull();
    }

    @Test
    void createSitesDataStepReturnsStep() {
        Step step = stepsConfig.createSitesDataStep("false");
        assertThat(step).isNotNull();
    }

    @Test
    void createChannelUserStepReturnsStep() {
        Step step = stepsConfig.createChannelUserStep();
        assertThat(step).isNotNull();
    }

    @Test
    void startLoggingStepShouldReturnStep() {
        Step step = stepsConfig.startLogging();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("loggingStep");
    }

    @Test
    void noOpWriterShouldReturnNonNullWriter() {
        ItemWriter<Object> writer = stepsConfig.noOpWriter();
        assertThat(writer).isNotNull();
    }

    @Test
    void getDryRunFlagShouldReturnTrueWhenParameterIsTrue() {
        JobParameters params = new JobParametersBuilder()
            .addString("dryRun", "true")
            .toJobParameters();

        JobExecution jobExecution = new JobExecution(1L, params);
        JobSynchronizationManager.register(jobExecution);

        assertThat(stepsConfig.getDryRunFlag()).isTrue();

        JobSynchronizationManager.close();
    }

    @Test
    void createReadStepWithDryRunTrueShouldReturnStep() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(new ByteArrayInputStream("field1\nvalue1".getBytes()));

        Step step = stepsConfig.createReadStep(
            "dryRunStep",
            mockResource,
            new String[]{"field1"},
            String.class,
            false,
            true
        );

        assertThat(step).isNotNull();
    }

    @Test
    void createReadStepWithWriteToCsvTrueShouldReturnStep() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(new ByteArrayInputStream("field1\nvalue1".getBytes()));

        Step step = stepsConfig.createReadStep(
            "writeCsvStep",
            mockResource,
            new String[]{"field1"},
            String.class,
            true,
            false
        );

        assertThat(step).isNotNull();
    }

    @Test
    void createCsvReaderShouldReturnReader() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        FlatFileItemReader<String> reader = stepsConfig.createCsvReader(
            mockResource,
            new String[]{"field1", "field2"},
            String.class
        );

        assertThat(reader).isNotNull();
    }

    @Test
    void createReadStepShouldReturnStepWithCorrectName() throws Exception {
        String stepName = "testStep";
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            stepName,
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo(stepName);
    }

    @Test
    void startLoggingStepShouldExecuteAndSetDebugFlag() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addString("debug", "true")
            .toJobParameters();
        JobExecution jobExecution = new JobExecution(99L, params);
        JobSynchronizationManager.register(jobExecution);

        Step step = stepsConfig.startLogging();

        step.execute(new org.springframework.batch.core.StepExecution(step.getName(), jobExecution));

        verify(loggingService).setDebugEnabled(true);
        verify(loggingService).initializeLogFile();
        verify(loggingService).logInfo("Job started with debug mode: true");
    }

    @Test
    void startLoggingStepShouldExecuteWithDebugFalse() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addString("debug", "false")
            .toJobParameters();
        JobExecution jobExecution = new JobExecution(99L, params);
        JobSynchronizationManager.register(jobExecution);

        Step step = stepsConfig.startLogging();

        step.execute(new org.springframework.batch.core.StepExecution(step.getName(), jobExecution));

        verify(loggingService).setDebugEnabled(false);
        verify(loggingService).initializeLogFile();
        verify(loggingService).logInfo("Job started with debug mode: false");
    }

    @Test
    void startLoggingStepShouldExecuteWithNoDebugParameter() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .toJobParameters();
        JobExecution jobExecution = new JobExecution(99L, params);
        JobSynchronizationManager.register(jobExecution);

        Step step = stepsConfig.startLogging();

        step.execute(new org.springframework.batch.core.StepExecution(step.getName(), jobExecution));

        verify(loggingService).setDebugEnabled(false);
        verify(loggingService).initializeLogFile();
        verify(loggingService).logInfo("Job started with debug mode: false");
    }

    @Test
    void getDryRunFlagShouldReturnFalseWhenParameterIsFalse() {
        JobParameters params = new JobParametersBuilder()
            .addString("dryRun", "false")
            .toJobParameters();

        JobExecution jobExecution = new JobExecution(1L, params);
        JobSynchronizationManager.register(jobExecution);

        assertThat(stepsConfig.getDryRunFlag()).isFalse();

        JobSynchronizationManager.close();
    }

    @Test
    void getDryRunFlagShouldReturnFalseWhenParameterIsNull() {
        JobParameters params = new JobParametersBuilder()
            .toJobParameters();

        JobExecution jobExecution = new JobExecution(1L, params);
        JobSynchronizationManager.register(jobExecution);

        assertThat(stepsConfig.getDryRunFlag()).isFalse();

        JobSynchronizationManager.close();
    }

    @Test
    void createCsvReaderShouldThrowExceptionWhenIOExceptionOccurs() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getFilename()).thenReturn("test.csv");
        when(mockResource.getInputStream()).thenThrow(new IOException("Test exception"));

        try {
            stepsConfig.createCsvReader(
                mockResource,
                new String[]{"field1", "field2"},
                String.class
            );
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Failed to create reader for file:");
            verify(loggingService).logError("Failed to create reader for file: {}test.csv" 
                + new IOException("Test exception"));
        }
    }

    @Test
    void createSitesDataStepWithDryRunTrue() {
        Step step = stepsConfig.createSitesDataStep("true");
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("sitesDataStep");
    }

    @Test
    void createChannelUserStepShouldUseDryRunFlag() {
        Step step = stepsConfig.createChannelUserStep();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("channelUserStep");
    }

    @Test
    void createArchiveListStepShouldUseDryRunFlag() {
        Step step = stepsConfig.createArchiveListStep();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("archiveListDataStep");
    }

}
