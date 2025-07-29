package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.jobs.ResolvedJobConfig;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.MigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolvedJobConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private CoreStepsConfig coreSteps;

    @Mock
    private MigrationRecordRepository migrationRecordRepository;

    @Mock
    private LoggingService loggingService;

    @Mock
    private ItemProcessor<Object, MigratedItemGroup> processor;

    @Mock
    private ItemWriter<MigratedItemGroup> writer;

    @Mock
    private ItemWriter<MigratedItemGroup> noOpWriter;

    @Mock
    private ItemWriter<Object> noOpObjectWriter;

    @Mock
    private Step createSitesDataStep;

    @Mock
    private Step createChannelUserStep;

    @Mock
    private Step createPreProcessStep;

    @Mock
    private Step createRobotUserSignInStep;

    @Mock
    private Step createWriteToCSVStep;

    @Mock
    private Step startLoggingStep;

    private ResolvedJobConfig resolvedJobConfig;

    @BeforeEach
    void setUp() {
        resolvedJobConfig = new ResolvedJobConfig(jobRepository, coreSteps, transactionManager);
    }

    @Test
    void shouldCreateResolvedMigrationRecordReaderWithRecords() {
        // Given
        List<MigrationRecord> resolvedRecords = List.of(
            new MigrationRecord(),
            new MigrationRecord()
        );
        when(migrationRecordRepository.findByStatus(VfMigrationStatus.RESOLVED)).thenReturn(resolvedRecords);

        // When
        ListItemReader<MigrationRecord> reader = resolvedJobConfig.resolvedMigrationRecordReader(
            migrationRecordRepository, loggingService
        );

        // Then
        assertThat(reader).isNotNull();
        verify(migrationRecordRepository).findByStatus(VfMigrationStatus.RESOLVED);
        verify(loggingService).logInfo("Found %d resolved migration records.", 2);
    }

    @Test
    void shouldCreateResolvedMigrationRecordReaderWithEmptyList() {
        // Given
        when(migrationRecordRepository.findByStatus(VfMigrationStatus.RESOLVED)).thenReturn(Collections.emptyList());

        // When
        ListItemReader<MigrationRecord> reader = resolvedJobConfig.resolvedMigrationRecordReader(
            migrationRecordRepository, loggingService
        );

        // Then
        assertThat(reader).isNotNull();
        verify(migrationRecordRepository).findByStatus(VfMigrationStatus.RESOLVED);
        verify(loggingService).logInfo("No resolved migration records found.");
    }

    @Test
    void shouldCreateResolvedMigrationRecordStepWithDryRunEnabled() {
        // Given
        ListItemReader<MigrationRecord> reader = new ListItemReader<>(Collections.emptyList());
        when(coreSteps.getDryRunFlag()).thenReturn(true);
        when(coreSteps.noOpWriter()).thenReturn(noOpObjectWriter);

        // When
        Step step = resolvedJobConfig.resolvedMigrationRecordStep(reader, processor, writer);

        // Then
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("resolvedMigrationRecordStep");
        verify(coreSteps).getDryRunFlag();
        verify(coreSteps).noOpWriter();
    }

    @Test
    void shouldCreateResolvedMigrationRecordStepWithDryRunDisabled() {
        // Given
        ListItemReader<MigrationRecord> reader = new ListItemReader<>(Collections.emptyList());
        when(coreSteps.getDryRunFlag()).thenReturn(false);

        // When
        Step step = resolvedJobConfig.resolvedMigrationRecordStep(reader, processor, writer);

        // Then
        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("resolvedMigrationRecordStep");
        verify(coreSteps).getDryRunFlag();
    }

    @Test
    void shouldCreateResolvedMigrationRecordJob() {
        // Given
        Step resolvedMigrationRecordStep = resolvedJobConfig.resolvedMigrationRecordStep(
            new ListItemReader<>(Collections.emptyList()), processor, writer
        );
        when(coreSteps.startLogging()).thenReturn(startLoggingStep);

        // When
        Job job = resolvedJobConfig.resolvedMigrationRecordJob(
            createSitesDataStep,
            createChannelUserStep,
            createPreProcessStep,
            createRobotUserSignInStep,
            resolvedMigrationRecordStep,
            createWriteToCSVStep
        );

        // Then
        assertThat(job).isNotNull();
        assertThat(job.getName()).isEqualTo("resolvedMigrationRecordJob");
        verify(coreSteps).startLogging();
    }

    @Test
    void shouldHaveCorrectJobFlow() {
        // Given
        Step resolvedMigrationRecordStep = resolvedJobConfig.resolvedMigrationRecordStep(
            new ListItemReader<>(Collections.emptyList()), processor, writer
        );
        when(coreSteps.startLogging()).thenReturn(startLoggingStep);

        // When
        Job job = resolvedJobConfig.resolvedMigrationRecordJob(
            createSitesDataStep,
            createChannelUserStep,
            createPreProcessStep,
            createRobotUserSignInStep,
            resolvedMigrationRecordStep,
            createWriteToCSVStep
        );

        // Then
        assertThat(job).isNotNull();
        // Verify the job has an incrementer
        assertThat(job.getJobParametersIncrementer()).isNotNull();
    }
}
