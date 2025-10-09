package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.PostMigrationItemProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.PostMigrationItemReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.EntityCreationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.PostMigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostMigrationJobConfigTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private CoreStepsConfig coreSteps;

    @Mock
    private LoggingService loggingService;

    @Mock
    private InMemoryCacheService cacheService;

    @Mock
    private EntityCreationService entityCreationService;

    @Mock
    private MigrationTrackerService migrationTrackerService;

    @Mock
    private MigrationRecordService migrationRecordService;

    @Mock
    private CaseService caseService;

    @Mock
    private BookingService bookingService;

    @Mock
    private RecordingService recordingService;

    @Mock
    private PostMigrationItemReader postMigrationItemReader;

    @Mock
    private PostMigrationItemProcessor postMigrationItemProcessor;

    @Mock
    private UserService userService;

    @Mock
    private StepContribution stepContribution;

    @Mock
    private ChunkContext chunkContext;

    @Mock
    private StepContext stepContext;

    private PostMigrationJobConfig config;

    @BeforeEach
    void setUp() throws Exception {
        config = new PostMigrationJobConfig(
            jobRepository,
            transactionManager,
            coreSteps,
            loggingService,
            cacheService,
            entityCreationService,
            migrationTrackerService,
            migrationRecordService,
            caseService,
            bookingService,
            recordingService,
            postMigrationItemReader,
            postMigrationItemProcessor,
            userService
        );

        Field emailField = PostMigrationJobConfig.class.getDeclaredField("vodafoneUserEmail");
        emailField.setAccessible(true);
        emailField.set(config, "robot@example.com");
    }

    @Test
    void postMigrationJobShouldNotBeNull() {
        Step dummy = mock(Step.class);
        when(coreSteps.startLogging()).thenReturn(dummy);

        Job job = config.postMigrationJob(dummy, dummy, dummy, dummy, dummy, dummy, dummy);

        assertThat(job).isNotNull();
    }

    @Test
    void createMarkCasesClosedStep_invokesStartNewReportRun() throws Exception {
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(Collections.emptyMap());
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(Collections.emptyList());

        Step step = config.createMarkCasesClosedStep();
        Tasklet tasklet = extractTasklet(step);

        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(migrationTrackerService).startNewReportRun();
        verify(migrationTrackerService, never()).writeCaseClosureReport();
    }

    @Test
    void createWriteReportsStep_invokesReportWriters() throws Exception {
        Step step = config.createWriteReportsStep();
        Tasklet tasklet = extractTasklet(step);

        tasklet.execute(stepContribution, chunkContext);

        verify(migrationTrackerService).writeNewUserReport();
        verify(migrationTrackerService).writeShareBookingsReport();
        verify(migrationTrackerService).writeShareInviteFailureReport();
    }

    private Tasklet extractTasklet(Step step) throws Exception {
        Field field = TaskletStep.class.getDeclaredField("tasklet");
        field.setAccessible(true);
        return (Tasklet) field.get(step);
    }

    @Test
    void createShareBookingsStep_shouldBuildStep() {
        PostMigrationWriter mockWriter = mock(PostMigrationWriter.class);
        @SuppressWarnings("unchecked")
        ItemReader<PostMigratedItemGroup> mockReader = mock(ItemReader.class);

        Step step = config.createShareBookingsStep(mockWriter, mockReader);

        assertThat(step).isNotNull();
        assertThat(step.getName()).isEqualTo("createShareBookingsStep");
    }

    @Test
    void postMigrationItemReaderBean_shouldCreateReader() {
        @SuppressWarnings("unchecked")
        ItemReader<PostMigratedItemGroup> mockReader = mock(ItemReader.class);
        when(postMigrationItemReader.createReader(false)).thenReturn(mockReader);
        
        ItemReader<PostMigratedItemGroup> reader = config.postMigrationItemReaderBean();

        assertThat(reader).isNotNull();
    }

    @Test
    void postMigrationJob_shouldBuildJobWithAllSteps() {
        Step mockCreateRobotUserSignInStep = mock(Step.class);
        Step mockCreateChannelUserStep = mock(Step.class);
        Step mockCreateMarkCasesClosedStep = mock(Step.class);
        Step mockCreatePreProcessStep = mock(Step.class);
        Step mockCreateShareBookingsStep = mock(Step.class);
        Step mockCreateWriteReportsStep = mock(Step.class);
        Step mockCreateWriteToCSVStep = mock(Step.class);

        Job job = config.postMigrationJob(
            mockCreateRobotUserSignInStep,
            mockCreateChannelUserStep,
            mockCreateMarkCasesClosedStep,
            mockCreatePreProcessStep,
            mockCreateShareBookingsStep,
            mockCreateWriteReportsStep,
            mockCreateWriteToCSVStep
        );

        assertThat(job).isNotNull();
        assertThat(job.getName()).isEqualTo("postMigrationJob");
    }
}
