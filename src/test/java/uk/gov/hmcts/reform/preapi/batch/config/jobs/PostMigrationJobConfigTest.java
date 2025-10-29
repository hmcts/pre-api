package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.JobSynchronizationManager;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.PostMigrationItemProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.PostMigrationItemReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.PostMigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private MigrationTrackerService migrationTrackerService;

    @Mock
    private CaseService caseService;

    @Mock
    private PostMigrationItemReader postMigrationItemReader;

    @Mock
    private PostMigrationItemProcessor postMigrationItemProcessor;

    @Mock
    private UserService userService;

    @Mock
    private PortalAccessRepository portalAccessRepository;

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
            migrationTrackerService,
            caseService,
            postMigrationItemReader,
            postMigrationItemProcessor,
            userService,
            portalAccessRepository
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

    @Test
    void createMarkCasesClosedStep_withVodafoneCases_shouldProcessCases() throws Exception {
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("dryRun", "false");
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(jobParams);
        
        CaseDTO caseDTO = createTestCaseDTO();
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        
        Map<String, List<String[]>> channelUsersMap = new HashMap<>();
        String[] userData = {"user", "email"};
        channelUsersMap.put("test-case", Collections.singletonList(userData));
        when(cacheService.getAllChannelReferences()).thenReturn(channelUsersMap);
        
        Step step = config.createMarkCasesClosedStep();
        Tasklet tasklet = extractTasklet(step);
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(migrationTrackerService).startNewReportRun();
        verify(migrationTrackerService).writeCaseClosureReport();
        verify(loggingService).logInfo("Found %d Vodafone-origin cases.", 1);
        verify(loggingService).logInfo("Loaded %d channel reference keys from cache.", 1);
    }

    @Test
    void createMarkCasesClosedStep_withDryRun_shouldNotCloseCases() throws Exception {
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("dryRun", "true");
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(jobParams);
        
        CaseDTO caseDTO = createTestCaseDTO();
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        
        Map<String, List<String[]>> channelUsersMap = new HashMap<>();
        when(cacheService.getAllChannelReferences()).thenReturn(channelUsersMap);
        
        Step step = config.createMarkCasesClosedStep();
        Tasklet tasklet = extractTasklet(step);
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(caseService, never()).upsert(any());
        verify(loggingService).logInfo("[DRY RUN] Would close Vodafone case: %s (%s).", 
            caseDTO.getReference(), caseDTO.getId());
    }

    @Test
    void createMarkCasesClosedStep_withClosedCase_shouldSkip() throws Exception {
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("dryRun", "false");
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(jobParams);
        
        CaseDTO caseDTO = createTestCaseDTO();
        caseDTO.setState(CaseState.CLOSED);
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        
        Map<String, List<String[]>> channelUsersMap = new HashMap<>();
        when(cacheService.getAllChannelReferences()).thenReturn(channelUsersMap);

        Step step = config.createMarkCasesClosedStep();
        Tasklet tasklet = extractTasklet(step);
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(loggingService).logInfo("Skipping case %s — already closed.", caseDTO.getReference());
        verify(migrationTrackerService).addCaseClosureEntry(any());
    }

    @Test
    void createMarkCasesClosedStep_withException_shouldHandleError() throws Exception {
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("dryRun", "false");
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(jobParams);
        
        CaseDTO caseDTO = createTestCaseDTO();
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        
        Map<String, List<String[]>> channelUsersMap = new HashMap<>();
        when(cacheService.getAllChannelReferences()).thenReturn(channelUsersMap);
        
        doThrow(new RuntimeException("Test error")).when(caseService).upsert(any());

        Step step = config.createMarkCasesClosedStep();
        Tasklet tasklet = extractTasklet(step);
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(loggingService).logError("Failed to close case %s (%s): %s — %s", 
            caseDTO.getReference(), caseDTO.getId(), "RuntimeException", "Test error");
        verify(migrationTrackerService).addCaseClosureEntry(any());
    }

    @Test
    void createMarkCasesClosedStep_withMatchingChannelUser_shouldSkipCase() throws Exception {
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("dryRun", "false");
        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(jobParams);
        
        CaseDTO caseDTO = createTestCaseDTO();
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        
        Map<String, List<String[]>> channelUsersMap = new HashMap<>();
        String[] userData = {"user", "email"};
        channelUsersMap.put("TEST-CASE-123", Collections.singletonList(userData));
        when(cacheService.getAllChannelReferences()).thenReturn(channelUsersMap);

        Step step = config.createMarkCasesClosedStep();
        Tasklet tasklet = extractTasklet(step);
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(loggingService).logInfo("Skipping case %s — matching channel user data found.", caseDTO.getReference());
        verify(migrationTrackerService).addCaseClosureEntry(any());
        verify(caseService, never()).upsert(any());
    }

    @Test
    void hasMatchingChannelUser_shouldFindMatches() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod(
            "hasMatchingChannelUser", String.class, Map.class);
        method.setAccessible(true);
        
        Map<String, List<String[]>> channelUsersMap = new HashMap<>();
        String[] userData1 = {"user", "email"};
        String[] userData2 = {"user2", "email2"};
        channelUsersMap.put("test-case-123", Collections.singletonList(userData1));
        channelUsersMap.put("other-case", Collections.singletonList(userData2));
        
        boolean result = (boolean) method.invoke(config, "CASE-123", channelUsersMap);
        assertThat(result).isTrue();
        
        boolean result2 = (boolean) method.invoke(config, "NOMATCH", channelUsersMap);
        assertThat(result2).isFalse();
    }

    @Test
    void buildClosedCaseDTO_shouldMapCorrectly() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod("buildClosedCaseDTO", 
            CaseDTO.class);
        method.setAccessible(true);
        
        CaseDTO caseDTO = createTestCaseDTO();
        ParticipantDTO participant = createTestParticipantDTO();
        caseDTO.setParticipants(List.of(participant));
        
        Object result = method.invoke(config, caseDTO);
        
        assertThat(result).isNotNull();
        assertThat(result.getClass().getSimpleName()).isEqualTo("CreateCaseDTO");
    }

    @Test
    void resolveEmailForShare_shouldFindEmailFromInvites() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod("resolveEmailForShare", 
            PostMigratedItemGroup.class, CreateShareBookingDTO.class);
        method.setAccessible(true);
        
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(UUID.randomUUID());
        invite.setEmail("test@example.com");
        item.setInvites(List.of(invite));
        
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setSharedWithUser(invite.getUserId());
        
        String result = (String) method.invoke(config, item, share);
        
        assertThat(result).isEqualTo("test@example.com");
    }

    @Test
    void resolveEmailForShare_shouldFallbackToUserService() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod("resolveEmailForShare", 
            PostMigratedItemGroup.class, CreateShareBookingDTO.class);
        method.setAccessible(true);
        
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        UUID userId = UUID.randomUUID();
        share.setSharedWithUser(userId);
        
        UserDTO userDTO = new UserDTO();
        userDTO.setEmail("user@example.com");
        when(userService.findById(userId)).thenReturn(userDTO);
        
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        String result = (String) method.invoke(config, item, share);
        
        assertThat(result).isEqualTo("user@example.com");
        verify(userService).findById(userId);
    }

    @Test
    void resolveEmailForShare_shouldHandleUserServiceException() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod("resolveEmailForShare", 
            PostMigratedItemGroup.class, CreateShareBookingDTO.class);
        method.setAccessible(true);
        
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        UUID userId = UUID.randomUUID();
        share.setSharedWithUser(userId);
        
        when(userService.findById(userId)).thenThrow(new RuntimeException("User not found"));
        
        String result = (String) method.invoke(config, item, share);
        
        assertThat(result).isEqualTo("");
        verify(loggingService).logWarning(
            "Could not find user email for ID: %s - %s", userId, "User not found");
    }

    @Test
    void createConditionalWriter_withDryRun_shouldSkipEntityCreation() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod("createConditionalWriter", 
            PostMigrationWriter.class);
        method.setAccessible(true);
        
        PostMigrationWriter mockWriter = mock(PostMigrationWriter.class);
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("test@example.com");
        item.setInvites(List.of(invite));
        
        CreateShareBookingDTO share = new CreateShareBookingDTO();
        share.setSharedWithUser(userId);
        item.setShareBookings(List.of(share));
        
        UserDTO activeUser = new UserDTO();
        activeUser.setId(userId);
        activeUser.setEmail("test@example.com");
        activeUser.setDeletedAt(null);
        
        when(userService.findById(userId)).thenReturn(activeUser);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId))
            .thenReturn(Optional.empty());
        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(userId))
            .thenReturn(List.of());
        
        Chunk<PostMigratedItemGroup> chunk = new Chunk<>();
        chunk.add(item);
        
        JobParameters jobParams = new JobParametersBuilder()
            .addString("dryRun", "true")
            .toJobParameters();
        JobExecution jobExecution = new JobExecution(1L, jobParams);
        JobSynchronizationManager.register(jobExecution);
        
        try {
            @SuppressWarnings("unchecked")
            ItemWriter<PostMigratedItemGroup> writer = (ItemWriter<PostMigratedItemGroup>) 
                method.invoke(config, mockWriter);
            assertThat(writer).isNotNull();
            
            writer.write(chunk);
            
            verify(loggingService).logInfo(
                "[DRY RUN] PostMigrationWriter processing %d item(s) - skipping entity creation", 1);
            verify(loggingService).logDebug("[DRY RUN] Processing post-migration item group: %s", item);
            verify(loggingService).logDebug("[DRY RUN] Successfully processed post-migration item");
            
            verify(migrationTrackerService).addInvitedUser(invite);
            verify(migrationTrackerService).addShareBooking(share);
            verify(migrationTrackerService).addShareBookingReport(share, 
                "test@example.com", "robot@example.com");
            
            verify(mockWriter, never()).write(any());
            
        } finally {
            JobSynchronizationManager.close();
        }
    }

    @Test
    void createConditionalWriter_withDryRunException_shouldHandleError() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod("createConditionalWriter", 
            PostMigrationWriter.class);
        method.setAccessible(true);
        
        PostMigrationWriter mockWriter = mock(PostMigrationWriter.class);
        PostMigratedItemGroup item = new PostMigratedItemGroup();
        
        UUID userId = UUID.randomUUID();
        CreateInviteDTO invite = new CreateInviteDTO();
        invite.setUserId(userId);
        invite.setEmail("test@example.com");
        item.setInvites(List.of(invite));
        
        UserDTO activeUser = new UserDTO();
        activeUser.setId(userId);
        activeUser.setEmail("test@example.com");
        activeUser.setDeletedAt(null);
        
        when(userService.findById(userId)).thenReturn(activeUser);
        when(portalAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId))
            .thenReturn(Optional.empty());
        when(portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(userId))
            .thenReturn(List.of());
        
        Chunk<PostMigratedItemGroup> chunk = new Chunk<>();
        chunk.add(item);
        
        JobParameters jobParams = new JobParametersBuilder()
            .addString("dryRun", "true")
            .toJobParameters();
        JobExecution jobExecution = new JobExecution(1L, jobParams);
        JobSynchronizationManager.register(jobExecution);
        
        try {
            doThrow(new RuntimeException("fail")).when(migrationTrackerService).addInvitedUser(invite);
            
            @SuppressWarnings("unchecked")
            ItemWriter<PostMigratedItemGroup> writer = (ItemWriter<PostMigratedItemGroup>) 
                method.invoke(config, mockWriter);
            assertThat(writer).isNotNull();
            
            writer.write(chunk);
            
            verify(loggingService).logError("[DRY RUN] Failed to process post-migration item: %s", "fail");
            
        } finally {
            JobSynchronizationManager.close();
        }
    }

    private CaseDTO createTestCaseDTO() {
        CaseDTO caseDTO = new CaseDTO();
        caseDTO.setId(UUID.randomUUID());
        caseDTO.setReference("TEST-CASE-123");
        caseDTO.setState(CaseState.OPEN);
        caseDTO.setOrigin(RecordingOrigin.VODAFONE);
        
        CourtDTO court = new CourtDTO();
        court.setId(UUID.randomUUID());
        caseDTO.setCourt(court);
        
        return caseDTO;
    }

    private BookingDTO createTestBookingDTO() {
        BookingDTO booking = new BookingDTO();
        booking.setId(UUID.randomUUID());
        
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        booking.setCaptureSessions(List.of(captureSession));
        
        return booking;
    }

    private RecordingDTO createTestRecordingDTO() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        return recording;
    }

    private ParticipantDTO createTestParticipantDTO() {
        ParticipantDTO participant = new ParticipantDTO();
        participant.setId(UUID.randomUUID());
        participant.setFirstName("John");
        participant.setLastName("Doe");
        participant.setParticipantType(ParticipantType.WITNESS);
        return participant;
    }
}
