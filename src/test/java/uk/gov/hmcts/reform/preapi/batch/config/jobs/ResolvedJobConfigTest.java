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
        List<MigrationRecord> resolvedRecords = List.of(
            new MigrationRecord(),
            new MigrationRecord()
        );
        when(migrationRecordRepository.findAllByStatusOrderedByVersion(VfMigrationStatus.SUBMITTED))
            .thenReturn(resolvedRecords);

        ListItemReader<MigrationRecord> reader = resolvedJobConfig.resolvedMigrationRecordReader(
            migrationRecordRepository, loggingService
        );

        assertThat(reader).isNotNull();
        verify(migrationRecordRepository).findAllByStatusOrderedByVersion(VfMigrationStatus.SUBMITTED);
        verify(loggingService).logInfo("Found %d resolved migration records.", 2);
    }

    @Test
    void shouldCreateResolvedMigrationRecordReaderWithEmptyList() {
        when(migrationRecordRepository.findAllByStatusOrderedByVersion(VfMigrationStatus.SUBMITTED))
            .thenReturn(Collections.emptyList());

        ListItemReader<MigrationRecord> reader = resolvedJobConfig.resolvedMigrationRecordReader(
            migrationRecordRepository, loggingService
        );

        assertThat(reader).isNotNull();
        verify(migrationRecordRepository).findAllByStatusOrderedByVersion(VfMigrationStatus.SUBMITTED);
        verify(loggingService).logInfo("No resolved migration records found.");
    }

    @Test
    void shouldCreateResolvedMigrationRecordStepWithDryRunEnabled() {
        ListItemReader<MigrationRecord> reader = new ListItemReader<>(Collections.emptyList());
        when(coreSteps.isDryRun()).thenReturn(true);
        when(coreSteps.noOpWriter()).thenReturn(noOpObjectWriter);

        Step step = resolvedJobConfig.resolvedMigrationRecordStep(reader, processor, writer);

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("resolvedMigrationRecordStep");
        verify(coreSteps).isDryRun();
        verify(coreSteps).noOpWriter();
    }

    @Test
    void shouldCreateResolvedMigrationRecordStepWithDryRunDisabled() {
        ListItemReader<MigrationRecord> reader = new ListItemReader<>(Collections.emptyList());
        when(coreSteps.isDryRun()).thenReturn(false);

        Step step = resolvedJobConfig.resolvedMigrationRecordStep(reader, processor, writer);

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("resolvedMigrationRecordStep");
        verify(coreSteps).isDryRun();
    }

    @Test
    void shouldCreateResolvedMigrationRecordJob() {
        Step resolvedMigrationRecordStep = resolvedJobConfig.resolvedMigrationRecordStep(
            new ListItemReader<>(Collections.emptyList()), processor, writer
        );
        when(coreSteps.startLogging()).thenReturn(startLoggingStep);

        Job job = resolvedJobConfig.resolvedMigrationRecordJob(
            createSitesDataStep,
            createChannelUserStep,
            createPreProcessStep,
            createRobotUserSignInStep,
            resolvedMigrationRecordStep,
            createWriteToCSVStep
        );

        assertThat(job).isNotNull();
        assertThat(job.getName()).isEqualTo("resolvedMigrationRecordJob");
        verify(coreSteps).startLogging();
    }

    @Test
    void shouldHaveCorrectJobFlow() {
        Step resolvedMigrationRecordStep = resolvedJobConfig.resolvedMigrationRecordStep(
            new ListItemReader<>(Collections.emptyList()), processor, writer
        );
        when(coreSteps.startLogging()).thenReturn(startLoggingStep);

        Job job = resolvedJobConfig.resolvedMigrationRecordJob(
            createSitesDataStep,
            createChannelUserStep,
            createPreProcessStep,
            createRobotUserSignInStep,
            resolvedMigrationRecordStep,
            createWriteToCSVStep
        );

        assertThat(job).isNotNull();
        assertThat(job.getJobParametersIncrementer()).isNotNull();
    }
}
