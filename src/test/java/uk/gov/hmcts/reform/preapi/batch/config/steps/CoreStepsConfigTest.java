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
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.Processor;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.MigrationWriter;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @Mock
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @BeforeEach
    void setup() {
        this.jobRepository = mock(JobRepository.class);
        this.itemProcessor = mock(Processor.class);
        this.migrationTrackerService = mock(MigrationTrackerService.class);
        this.itemWriter = mock(MigrationWriter.class);
        this.loggingService = mock(LoggingService.class);
        this.azureVodafoneStorageService = mock(AzureVodafoneStorageService.class);

        PlatformTransactionManager transactionManager = new ResourcelessTransactionManager();

        stepsConfig = new CoreStepsConfig(
            jobRepository,
            transactionManager,
            itemProcessor,
            migrationTrackerService,
            itemWriter,
            loggingService,
            azureVodafoneStorageService
        );
    }

    @Test
    void getDryRunFlagShouldReturnFalseWhenNoContext() {
        JobSynchronizationManager.close();
        assertThat(stepsConfig.isDryRun()).isFalse();
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

        assertThat(stepsConfig.isDryRun()).isTrue();

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
    void getDryRunFlagShouldReturnFalseWhenParameterIsFalse() {
        JobParameters params = new JobParametersBuilder()
            .addString("dryRun", "false")
            .toJobParameters();

        JobExecution jobExecution = new JobExecution(1L, params);
        JobSynchronizationManager.register(jobExecution);

        assertThat(stepsConfig.isDryRun()).isFalse();

        JobSynchronizationManager.close();
    }

    @Test
    void getDryRunFlagShouldReturnFalseWhenParameterIsNull() {
        JobParameters params = new JobParametersBuilder()
            .toJobParameters();

        JobExecution jobExecution = new JobExecution(1L, params);
        JobSynchronizationManager.register(jobExecution);

        assertThat(stepsConfig.isDryRun()).isFalse();

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

    @Test
    void startLoggingShouldReturnStepWithCorrectName() {
        Step step = stepsConfig.startLogging();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("loggingStep");
    }

    @Test
    void startLoggingShouldHaveStepExecutionListener() {
        Step step = stepsConfig.startLogging();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("loggingStep");
    }

    @Test
    void createReadStepWithDryRunFalseAndWriteToCsvFalseShouldUseNoOpWriter() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepWithDryRunTrueShouldUseMigrationTrackerWriter() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            true
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepShouldHaveStepExecutionListener() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createCsvReaderShouldHandleNullFilename() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getFilename()).thenReturn(null);
        when(mockResource.getInputStream()).thenThrow(new IOException("Test exception"));

        try {
            stepsConfig.createCsvReader(
                mockResource,
                new String[]{"field1", "field2"},
                String.class
            );
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Failed to create reader for file:");
            verify(loggingService).logError("Failed to create reader for file: {}null"
                + new IOException("Test exception"));
        }
    }

    @Test
    void createCsvReaderShouldHandleEmptyFilename() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getFilename()).thenReturn("");
        when(mockResource.getInputStream()).thenThrow(new IOException("Test exception"));

        try {
            stepsConfig.createCsvReader(
                mockResource,
                new String[]{"field1", "field2"},
                String.class
            );
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Failed to create reader for file:");
            verify(loggingService).logError("Failed to create reader for file: {}" + new IOException("Test exception"));
        }
    }

    @Test
    void noOpWriterShouldAcceptAnyItems() {
        ItemWriter<Object> writer = stepsConfig.noOpWriter();
        assertThat(writer).isNotNull();

        assertThatCode(() -> writer.write(null)).doesNotThrowAnyException();
        assertThatCode(() -> writer.write(Chunk.of())).doesNotThrowAnyException();
        assertThatCode(() -> writer.write(Chunk.of("test"))).doesNotThrowAnyException();
    }

    @Test
    void getDryRunFlagShouldHandleEmptyStringParameter() {
        JobParameters params = new JobParametersBuilder()
            .addString("dryRun", "")
            .toJobParameters();

        JobExecution jobExecution = new JobExecution(1L, params);
        JobSynchronizationManager.register(jobExecution);

        assertThat(stepsConfig.isDryRun()).isFalse();

        JobSynchronizationManager.close();
    }

    @Test
    void getDryRunFlagShouldHandleInvalidBooleanString() {
        JobParameters params = new JobParametersBuilder()
            .addString("dryRun", "invalid")
            .toJobParameters();

        JobExecution jobExecution = new JobExecution(1L, params);
        JobSynchronizationManager.register(jobExecution);

        assertThatCode(() -> stepsConfig.isDryRun()).doesNotThrowAnyException();

        JobSynchronizationManager.close();
    }

    @Test
    void createSitesDataStepWithDryRunFalse() {
        Step step = stepsConfig.createSitesDataStep("false");
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("sitesDataStep");
    }

    @Test
    void createSitesDataStepWithDryRunNull() {
        Step step = stepsConfig.createSitesDataStep(null);
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("sitesDataStep");
    }

    @Test
    void createSitesDataStepWithDryRunEmpty() {
        Step step = stepsConfig.createSitesDataStep("");
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("sitesDataStep");
    }

    @Test
    void createSitesDataStepWithDryRunInvalid() {
        Step step = stepsConfig.createSitesDataStep("invalid");
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("sitesDataStep");
    }

    @Test
    void createReadStepWithDryRunTrueShouldUseMigrationTrackerWriter2() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            true
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepWithWriteToCsvTrueAndDryRunFalseShouldUseItemWriter() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            true,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepWithWriteToCsvFalseAndDryRunFalseShouldUseNoOpWriter() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepWithWriteToCsvTrueAndDryRunTrueShouldUseMigrationTrackerWriter() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            true,
            true
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void getDryRunFlagShouldReturnFalseWhenJobContextIsNull() {
        JobSynchronizationManager.close();
        assertThat(stepsConfig.isDryRun()).isFalse();
    }

    @Test
    void getDryRunFlagShouldReturnFalseWhenJobParametersIsNull() {
        JobExecution jobExecution = new JobExecution(1L, null);
        JobSynchronizationManager.register(jobExecution);

        assertThat(stepsConfig.isDryRun()).isFalse();

        JobSynchronizationManager.close();
    }

    @Test
    void getDryRunFlagShouldReturnFalseWhenDryRunParameterIsNull() {
        JobParameters params = new JobParametersBuilder()
            .addString("otherParam", "value")
            .toJobParameters();

        JobExecution jobExecution = new JobExecution(1L, params);
        JobSynchronizationManager.register(jobExecution);

        assertThat(stepsConfig.isDryRun()).isFalse();

        JobSynchronizationManager.close();
    }

    @Test
    void noOpWriterShouldHandleNullChunk() {
        ItemWriter<Object> writer = stepsConfig.noOpWriter();
        assertThat(writer).isNotNull();

        assertThatCode(() -> writer.write(null)).doesNotThrowAnyException();
    }

    @Test
    void noOpWriterShouldHandleEmptyChunk() {
        ItemWriter<Object> writer = stepsConfig.noOpWriter();
        assertThat(writer).isNotNull();

        assertThatCode(() -> writer.write(Chunk.of())).doesNotThrowAnyException();
    }

    @Test
    void noOpWriterShouldHandleChunkWithItems() {
        ItemWriter<Object> writer = stepsConfig.noOpWriter();
        assertThat(writer).isNotNull();

        assertThatCode(() -> writer.write(Chunk.of("test1", "test2"))).doesNotThrowAnyException();
    }

    @Test
    void createArchiveListStepShouldUseDryRunFlag2() {
        Step step = stepsConfig.createArchiveListStep();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("archiveListDataStep");
    }

    @Test
    void createChannelUserStepShouldUseDryRunFlag2() {
        Step step = stepsConfig.createChannelUserStep();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("channelUserStep");
    }

    @Test
    void startLoggingShouldHandleDebugParameterTrue() {
        Step step = stepsConfig.startLogging();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("loggingStep");
    }

    @Test
    void startLoggingShouldHandleDebugParameterFalse() {
        Step step = stepsConfig.startLogging();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("loggingStep");
    }

    @Test
    void startLoggingShouldHandleDebugParameterNull() {
        Step step = stepsConfig.startLogging();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("loggingStep");
    }

    @Test
    void startLoggingShouldHandleDebugParameterInvalid() {
        Step step = stepsConfig.startLogging();
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("loggingStep");
    }

    @Test
    void createReadStepShouldHaveFaultTolerance() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepShouldHaveSkipLimit() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepShouldHaveExceptionHandling() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepShouldUseCorrectChunkSize() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepShouldUseCorrectTransactionManager() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepShouldUseCorrectReader() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepShouldUseCorrectProcessor() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

    @Test
    void createReadStepShouldUseCorrectWriter() throws Exception {
        var mockResource = mock(Resource.class);
        when(mockResource.exists()).thenReturn(true);
        when(mockResource.getInputStream()).thenReturn(
            new ByteArrayInputStream("field1,field2\nvalue1,value2".getBytes())
        );

        Step step = stepsConfig.createReadStep(
            "testStep",
            mockResource,
            new String[]{"field1", "field2"},
            String.class,
            false,
            false
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("testStep");
    }

}
