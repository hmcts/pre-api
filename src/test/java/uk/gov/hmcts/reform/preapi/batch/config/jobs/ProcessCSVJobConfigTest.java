package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
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
    }

    @Test
    void shouldCreatePendingMigrationRecordStepWithDryRunWriter() {
        when(coreStepsConfig.isDryRun()).thenReturn(true);
        when(coreStepsConfig.noOpWriter()).thenReturn(noOpWriter);

        Step result = processCSVJobConfig.pendingMigrationRecordStep(
            pendingMigrationRecordReader,
            processor,
            writer
        );

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("pendingMigrationRecordStep");
        verify(coreStepsConfig).isDryRun();
        verify(coreStepsConfig).noOpWriter();
    }

    @Test
    void shouldCreatePendingMigrationRecordStepWithRegularWriter() {
        when(coreStepsConfig.isDryRun()).thenReturn(false);

        Step result = processCSVJobConfig.pendingMigrationRecordStep(
            pendingMigrationRecordReader,
            processor,
            writer
        );

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("pendingMigrationRecordStep");
        verify(coreStepsConfig).isDryRun();
    }

    @Test
    void shouldCreatePendingMigrationRecordReaderWithRecords() {
        List<MigrationRecord> pendingRecords = Arrays.asList(migrationRecord1, migrationRecord2);
        when(migrationRecordRepository.findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING))
            .thenReturn(pendingRecords);

        ListItemReader<MigrationRecord> result = processCSVJobConfig.pendingMigrationRecordReader(
            migrationRecordRepository,
            loggingService
        );

        assertThat(result).isNotNull();
        verify(migrationRecordRepository).findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING);
        verify(loggingService).logInfo("Found %d pending migration records.", 2);
    }

    @Test
    void shouldCreatePendingMigrationRecordReaderWithEmptyList() {
        List<MigrationRecord> emptyList = Collections.emptyList();
        when(migrationRecordRepository.findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING))
            .thenReturn(emptyList);

        ListItemReader<MigrationRecord> result = processCSVJobConfig.pendingMigrationRecordReader(
            migrationRecordRepository,
            loggingService
        );

        assertThat(result).isNotNull();
        verify(migrationRecordRepository).findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING);
        verify(loggingService).logInfo("No pending migration records found.");
    }

    @Test
    void shouldHaveCorrectConstructorFields() {
        assertThat(processCSVJobConfig.transactionManager).isEqualTo(transactionManager);
    }


    @Test
    void shouldLogStartRunInPendingMigrationRecordReader() {
        List<MigrationRecord> pendingRecords = Arrays.asList(migrationRecord1, migrationRecord2);
        when(migrationRecordRepository.findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING))
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
        when(migrationRecordRepository.findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING))
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

    @Test
    void shouldCreateProcessCSVJobWithAllRequiredSteps() {
        when(batchConfiguration.fileAvailabilityDecider()).thenReturn(fileAvailabilityDecider);
        when(coreStepsConfig.startLogging()).thenReturn(startLoggingStep);

        Job job = processCSVJobConfig.processCSVJob(
            createSitesDataStep,
            createChannelUserStep,
            createRobotUserSignInStep,
            createPreProcessStep,
            createPreProcessMetadataStep,
            createArchiveListStep,
            createWriteToCSVStep,
            pendingMigrationRecordStep
        );

        assertThat(job).isNotNull();
        assertThat(job.getName()).isEqualTo("processCSVJob");
        assertThat(job.getJobParametersIncrementer()).isNotNull();
    }

    @Test
    void shouldCreatePendingMigrationRecordStepWithCorrectConfiguration() {
        when(coreStepsConfig.isDryRun()).thenReturn(false);

        Step step = processCSVJobConfig.pendingMigrationRecordStep(
            pendingMigrationRecordReader,
            processor,
            writer
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("pendingMigrationRecordStep");
        verify(coreStepsConfig).isDryRun();
    }

    @Test
    void shouldCreatePendingMigrationRecordStepWithFaultTolerance() {
        when(coreStepsConfig.isDryRun()).thenReturn(false);

        Step step = processCSVJobConfig.pendingMigrationRecordStep(
            pendingMigrationRecordReader,
            processor,
            writer
        );

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("pendingMigrationRecordStep");
        assertThat(step).isNotNull();
    }

    @Test
    void shouldCreatePendingMigrationRecordReaderWithSingleRecord() {
        List<MigrationRecord> singleRecord = Collections.singletonList(migrationRecord1);
        when(migrationRecordRepository.findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING))
            .thenReturn(singleRecord);

        ListItemReader<MigrationRecord> result = processCSVJobConfig.pendingMigrationRecordReader(
            migrationRecordRepository,
            loggingService
        );

        assertThat(result).isNotNull();
        verify(migrationRecordRepository).findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING);
        verify(loggingService).startRun("pending migration records", 1);
        verify(loggingService).logInfo("Found %d pending migration records.", 1);
    }

    @Test
    void shouldCreatePendingMigrationRecordReaderWithLargeList() {
        List<MigrationRecord> largeList = Collections.nCopies(100, migrationRecord1);
        when(migrationRecordRepository.findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING))
            .thenReturn(largeList);

        ListItemReader<MigrationRecord> result = processCSVJobConfig.pendingMigrationRecordReader(
            migrationRecordRepository,
            loggingService
        );

        assertThat(result).isNotNull();
        verify(migrationRecordRepository).findAllByStatusOrderedByVersion(VfMigrationStatus.PENDING);
        verify(loggingService).startRun("pending migration records", 100);
        verify(loggingService).logInfo("Found %d pending migration records.", 100);
    }

    @Test
    void shouldCreateProcessCSVJobWithIncrementer() {
        when(batchConfiguration.fileAvailabilityDecider()).thenReturn(fileAvailabilityDecider);
        when(coreStepsConfig.startLogging()).thenReturn(startLoggingStep);

        Job job = processCSVJobConfig.processCSVJob(
            createSitesDataStep,
            createChannelUserStep,
            createRobotUserSignInStep,
            createPreProcessStep,
            createPreProcessMetadataStep,
            createArchiveListStep,
            createWriteToCSVStep,
            pendingMigrationRecordStep
        );

        assertThat(job).isNotNull();
        assertThat(job.getJobParametersIncrementer()).isNotNull();
    }


}


