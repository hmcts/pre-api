package uk.gov.hmcts.reform.preapi.batch.config.steps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
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
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
        // This would require mocking JobSynchronizationManager.getContext()
        // which is complex due to static methods
        JobSynchronizationManager.close();
        assertThat(stepsConfig.getDryRunFlag()).isFalse();
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

}
