package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.config.jobs.ProcessCSVJobConfig;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProcessCSVJobConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private BatchConfiguration batchConfiguration;

    @Mock
    private CoreStepsConfig coreStepsConfig;

    @Mock
    private JobExecutionDecider fileAvailabilityDecider;

    @Mock
    private Step startLoggingStep;

    @Mock
    private Step createSitesDataStep;

    @Mock
    private Step createChannelUserStep;

    @Mock
    private Step createRobotUserSignInStep;

    @Mock
    private Step createPreProcessStep;

    @Mock
    private Step createPreProcessMetadataStep;

    @Mock
    private Step createArchiveListStep;

    @Mock
    private Step createWriteToCSVStep;

    @Mock
    private Step pendingMigrationRecordStep;

    @Mock
    private ListItemReader<MigrationRecord> pendingMigrationRecordReader;

    @Mock
    private ItemProcessor<Object, MigratedItemGroup> processor;

    @Mock
    private ItemWriter<MigratedItemGroup> writer;

    @Mock
    private ItemWriter<Object> noOpWriter;

    @Mock
    private MigrationRecordRepository migrationRecordRepository;

    @Mock
    private LoggingService loggingService;

    @Mock
    private MigrationRecord migrationRecord1;

    @Mock
    private MigrationRecord migrationRecord2;

    private ProcessCSVJobConfig processCSVJobConfig;

    @BeforeEach
    void setUp() {
        processCSVJobConfig = new ProcessCSVJobConfig(
            jobRepository,
            transactionManager,
            batchConfiguration,
            coreStepsConfig
        );
    }

    @Test
    void shouldCreateProcessCSVJobWithAllSteps() {
        // Given
        when(batchConfiguration.fileAvailabilityDecider()).thenReturn(fileAvailabilityDecider);
        when(coreStepsConfig.startLogging()).thenReturn(startLoggingStep);

        // When
        Job result = processCSVJobConfig.processCSVJob(
            createSitesDataStep,
            createChannelUserStep,
            createRobotUserSignInStep,
            createPreProcessStep,
            createPreProcessMetadataStep,
            createArchiveListStep,
            createWriteToCSVStep,
            pendingMigrationRecordStep
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("processCSVJob");
    }

    @Test
    void shouldCreatePendingMigrationRecordStepWithDryRunWriter() {
        // Given
        when(coreStepsConfig.getDryRunFlag()).thenReturn(true);
        when(coreStepsConfig.noOpWriter()).thenReturn(noOpWriter);

        // When
        Step result = processCSVJobConfig.pendingMigrationRecordStep(
            pendingMigrationRecordReader,
            processor,
            writer
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("pendingMigrationRecordStep");
        verify(coreStepsConfig).getDryRunFlag();
        verify(coreStepsConfig).noOpWriter();
    }

    @Test
    void shouldCreatePendingMigrationRecordStepWithRegularWriter() {
        // Given
        when(coreStepsConfig.getDryRunFlag()).thenReturn(false);

        // When
        Step result = processCSVJobConfig.pendingMigrationRecordStep(
            pendingMigrationRecordReader,
            processor,
            writer
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("pendingMigrationRecordStep");
        verify(coreStepsConfig).getDryRunFlag();
    }

    @Test
    void shouldCreatePendingMigrationRecordReaderWithRecords() {
        // Given
        List<MigrationRecord> pendingRecords = Arrays.asList(migrationRecord1, migrationRecord2);
        when(migrationRecordRepository.findAllByStatus(VfMigrationStatus.PENDING))
            .thenReturn(pendingRecords);

        // When
        ListItemReader<MigrationRecord> result = processCSVJobConfig.pendingMigrationRecordReader(
            migrationRecordRepository,
            loggingService
        );

        // Then
        assertThat(result).isNotNull();
        verify(migrationRecordRepository).findAllByStatus(VfMigrationStatus.PENDING);
        verify(loggingService).logInfo("Found %d pending migration records.", 2);
    }

    @Test
    void shouldCreatePendingMigrationRecordReaderWithEmptyList() {
        // Given
        List<MigrationRecord> emptyList = Collections.emptyList();
        when(migrationRecordRepository.findAllByStatus(VfMigrationStatus.PENDING))
            .thenReturn(emptyList);

        // When
        ListItemReader<MigrationRecord> result = processCSVJobConfig.pendingMigrationRecordReader(
            migrationRecordRepository,
            loggingService
        );

        // Then
        assertThat(result).isNotNull();
        verify(migrationRecordRepository).findAllByStatus(VfMigrationStatus.PENDING);
        verify(loggingService).logInfo("No pending migration records found.");
    }

    @Test
    void shouldHaveCorrectConstructorFields() {
        // Then
        assertThat(processCSVJobConfig.transactionManager).isEqualTo(transactionManager);
    }


    @Test
    void shouldLogStartRunInPendingMigrationRecordReader() {
        List<MigrationRecord> pendingRecords = Arrays.asList(migrationRecord1, migrationRecord2);
        when(migrationRecordRepository.findAllByStatus(VfMigrationStatus.PENDING))
            .thenReturn(pendingRecords);

        ListItemReader<MigrationRecord> result = processCSVJobConfig.pendingMigrationRecordReader(
            migrationRecordRepository,
            loggingService
        );

        assertThat(result).isNotNull();
        verify(loggingService).startRun("pending migration records", 2);
    }

    @Test
    void shouldLogStartRunWithEmptyListInPendingMigrationRecordReader() {
        List<MigrationRecord> emptyList = Collections.emptyList();
        when(migrationRecordRepository.findAllByStatus(VfMigrationStatus.PENDING))
            .thenReturn(emptyList);

        ListItemReader<MigrationRecord> result = processCSVJobConfig.pendingMigrationRecordReader(
            migrationRecordRepository,
            loggingService
        );

        assertThat(result).isNotNull();
        verify(loggingService).startRun("pending migration records", 0);
    }

    @Test
    void shouldCreateProcessCSVJobWithCorrectFlow() {
        when(batchConfiguration.fileAvailabilityDecider()).thenReturn(fileAvailabilityDecider);
        when(coreStepsConfig.startLogging()).thenReturn(startLoggingStep);

        Job result = processCSVJobConfig.processCSVJob(
            createSitesDataStep,
            createChannelUserStep,
            createRobotUserSignInStep,
            createPreProcessStep,
            createPreProcessMetadataStep,
            createArchiveListStep,
            createWriteToCSVStep,
            pendingMigrationRecordStep
        );

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("processCSVJob");
        verify(batchConfiguration).fileAvailabilityDecider();
        verify(coreStepsConfig).startLogging();
    }

}
