package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.EntityCreationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.PostMigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PostMigrationJobConfigTest {

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
    private Step dummyStep;
    @Mock
    private PostMigrationWriter postMigrationWriter;
    @Mock
    private StepContribution stepContribution;
    @Mock
    private ChunkContext chunkContext;
    @Mock
    private StepContext stepContext;

    private PostMigrationJobConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
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
            recordingService
        );

        when(chunkContext.getStepContext()).thenReturn(stepContext);
        when(stepContext.getJobParameters()).thenReturn(Collections.emptyMap());
    }

    // --------------------------------------------------
    // existence
    // --------------------------------------------------

    @Test
    void postMigrationJobShouldNotBeNull() {
        when(coreSteps.startLogging()).thenReturn(dummyStep);
        Job job = config.postMigrationJob(dummyStep, dummyStep, dummyStep, dummyStep, dummyStep, dummyStep);
        assertThat(job).isNotNull();
    }

    @Test
    void createMarkCasesClosedStepShouldNotBeNull() {
        Step step = config.createMarkCasesClosedStep();
        assertThat(step).isNotNull();
    }

    @Test
    void createShareBookingsStepShouldNotBeNull() {
        Step step = config.createShareBookingsStep(postMigrationWriter);
        assertThat(step).isNotNull();
    }

    // --------------------------------------------------
    // markCasesClosedStep tests
    // --------------------------------------------------

    @Test
    void markCasesClosedStep_handlesNoVodafoneCases() throws Exception {
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(Collections.emptyList());

        Tasklet tasklet = getTaskletFromStep(config.createMarkCasesClosedStep());
        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(loggingService).logInfo("No Vodafone-origin cases found.");
        verify(migrationTrackerService, never()).writeCaseClosureReport();
    }

    @Test
    void markCasesClosedStep_closesCaseWhenNoChannelUser() throws Exception {
        CaseDTO caseDTO = createTestCase("REF1", UUID.randomUUID());
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        when(cacheService.getAllChannelReferences()).thenReturn(Collections.emptyMap());
        when(bookingService.findAllByCaseId(eq(caseDTO.getId()), any()))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        Tasklet tasklet = getTaskletFromStep(config.createMarkCasesClosedStep());
        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(caseService).upsert(any(CreateCaseDTO.class));
        verify(loggingService).logInfo(
            "Closed Vodafone case: %s (%s). Removed %d recording(s).",
            "REF1",
            caseDTO.getId(),
            0
        );
        verify(migrationTrackerService).writeCaseClosureReport();
    }

    @Test
    void markCasesClosedStep_skipsCaseWhenChannelUserExists() throws Exception {
        CaseDTO caseDTO = createTestCase("REF2", UUID.randomUUID());
        Map<String, List<String[]>> channelUsersMap = Map.of(
            "ref2", new ArrayList<>() {
                {
                    add(new String[]{"user"});
                    add(new String[]{"user"});
                }
            }
        );

        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        when(cacheService.getAllChannelReferences()).thenReturn(channelUsersMap);

        Tasklet tasklet = getTaskletFromStep(config.createMarkCasesClosedStep());
        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(caseService, never()).upsert(any());
        verify(loggingService).logInfo("Skipping case %s — matching channel user data found.", "REF2");
        verify(migrationTrackerService).writeCaseClosureReport();
    }

    @Test
    void markCasesClosedStep_skipsCaseAlreadyClosed() throws Exception {
        CaseDTO caseDTO = createTestCase("REF_CLOSED", UUID.randomUUID());
        caseDTO.setState(CaseState.CLOSED);
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        when(cacheService.getAllChannelReferences()).thenReturn(Collections.emptyMap());

        Tasklet tasklet = getTaskletFromStep(config.createMarkCasesClosedStep());
        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(caseService, never()).upsert(any());
        verify(loggingService).logInfo("Skipping case %s — already closed.", "REF_CLOSED");
        verify(migrationTrackerService).addCaseClosureEntry(any());
        verify(migrationTrackerService).writeCaseClosureReport();
    }

    @Test
    void markCasesClosedStep_dryRunMode() throws Exception {
        Map<String, Object> jobParams = Map.of("dryRun", "true");
        when(stepContext.getJobParameters()).thenReturn(jobParams);

        CaseDTO caseDTO = createTestCase("REF3", UUID.randomUUID());
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        when(cacheService.getAllChannelReferences()).thenReturn(Collections.emptyMap());
        when(bookingService.findAllByCaseId(eq(caseDTO.getId()), any()))
            .thenReturn(new PageImpl<>(Collections.emptyList()));

        Tasklet tasklet = getTaskletFromStep(config.createMarkCasesClosedStep());
        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(caseService, never()).upsert(any());
        verify(loggingService).logInfo(
            "[DRY RUN] Would close Vodafone case: %s (%s). Would remove %d recording(s).",
            "REF3",
            caseDTO.getId(),
            0
        );
        verify(migrationTrackerService).writeCaseClosureReport();
    }

    @Test
    void markCasesClosedStep_logsErrorWhenUpsertFails() throws Exception {
        CaseDTO caseDTO = createTestCase("REF_ERR", UUID.randomUUID());
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(List.of(caseDTO));
        when(cacheService.getAllChannelReferences()).thenReturn(Collections.emptyMap());
        when(bookingService.findAllByCaseId(eq(caseDTO.getId()), any()))
            .thenReturn(new PageImpl<>(Collections.emptyList()));
        doThrow(new RuntimeException("fail")).when(caseService).upsert(any());

        Tasklet tasklet = getTaskletFromStep(config.createMarkCasesClosedStep());
        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(loggingService).logError(
            "Failed to close case %s (%s): %s — %s",
            "REF_ERR",
            caseDTO.getId(),
            "RuntimeException",
            "fail"
        );
        verify(migrationTrackerService).writeCaseClosureReport();
    }


    // --------------------------------------------------
    // shareBookings step tests
    // --------------------------------------------------

    @Test
    void shareBookingsStep_handlesNoBookings() throws Exception {
        when(migrationRecordService.findShareableOrigs()).thenReturn(Collections.emptyList());
        when(cacheService.getAllChannelReferences()).thenReturn(Collections.emptyMap());

        Tasklet tasklet = getTaskletFromStep(config.createShareBookingsStep(postMigrationWriter));
        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(loggingService).logInfo("Share booking creation complete. Total created: %d", 0);
        verify(postMigrationWriter).write(any());
        verify(entityCreationService, never()).createShareBookingAndInviteIfNotExists(any(), any(), any(), any());
        verify(migrationTrackerService).writeShareInviteFailureReport();
    }

    @Test
    void shareBookingsStep_skipsWhenNoChannelMatch() throws Exception {
        UUID bookingId = UUID.randomUUID();
        MigrationRecord rec = new MigrationRecord();
        rec.setArchiveId("ARCH_NO_MATCH");
        rec.setRecordingGroupKey("REFX|john|doe|2024-01-01");
        rec.setBookingId(bookingId);

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(rec));
        when(cacheService.getAllChannelReferences()).thenReturn(
            Map.of("some.other.key", List.<String[]>of(new String[]{"n/a","n/a"}))
        );

        Tasklet tasklet = getTaskletFromStep(config.createShareBookingsStep(postMigrationWriter));
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(entityCreationService, never()).createShareBookingAndInviteIfNotExists(any(), any(), any(), any());
        verify(postMigrationWriter).write(any());
        verify(migrationTrackerService).writeShareInviteFailureReport();
    }

    @Test
    void shareBookingsStep_skipsWhenBookingIdNull() throws Exception {
        MigrationRecord rec = new MigrationRecord();
        rec.setArchiveId("ARCH_NULL_BOOKING");
        rec.setRecordingGroupKey("REF1|john|doe|2024-01-01");
        rec.setBookingId(null);

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(rec));
        when(cacheService.getAllChannelReferences()).thenReturn(
            Map.of("ref1.john.doe.20240101", List.<String[]>of(new String[] {"john.doe", "j@test.com"}))
        );

        Tasklet tasklet = getTaskletFromStep(config.createShareBookingsStep(postMigrationWriter));
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(bookingService, never()).findById(any());
        verify(entityCreationService, never()).createShareBookingAndInviteIfNotExists(any(), any(), any(), any());
        verify(postMigrationWriter).write(any());
        verify(migrationTrackerService).writeShareInviteFailureReport();
    }

    @Test
    void shareBookingsStep_skipsWhenBookingLookupThrows() throws Exception {
        UUID bookingId = UUID.randomUUID();
        MigrationRecord rec = new MigrationRecord();
        rec.setArchiveId("ARCH_THROW");
        rec.setRecordingGroupKey("REF2|john|doe|2024-01-01");
        rec.setBookingId(bookingId);

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(rec));
        when(cacheService.getAllChannelReferences()).thenReturn(
            Map.of("ref2.john.doe.240101", List.<String[]>of(new String[]{"john.doe","j@test.com"}))
        );
        when(bookingService.findById(bookingId)).thenThrow(new RuntimeException("boom"));

        Tasklet tasklet = getTaskletFromStep(config.createShareBookingsStep(postMigrationWriter));
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(entityCreationService, never()).createShareBookingAndInviteIfNotExists(any(), any(), any(), any());
        verify(postMigrationWriter).write(any());
        verify(migrationTrackerService).writeShareInviteFailureReport();
    }

    @Test
    void shareBookingsStep_skipsWhenBookingLookupReturnsNull() throws Exception {
        UUID bookingId = UUID.randomUUID();
        MigrationRecord rec = new MigrationRecord();
        rec.setArchiveId("ARCH_NULL");
        rec.setRecordingGroupKey("REF3|john|doe|2024-01-01");
        rec.setBookingId(bookingId);

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(rec));
        when(cacheService.getAllChannelReferences()).thenReturn(
            Map.of("ref3.john.doe.2024-01-01", List.<String[]>of(new String[]{"john.doe","j@test.com"}))
        );
        when(bookingService.findById(bookingId)).thenReturn(null);

        Tasklet tasklet = getTaskletFromStep(config.createShareBookingsStep(postMigrationWriter));
        RepeatStatus status = tasklet.execute(stepContribution, chunkContext);

        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(entityCreationService, never()).createShareBookingAndInviteIfNotExists(any(), any(), any(), any());
        verify(postMigrationWriter).write(any());
        verify(migrationTrackerService).writeShareInviteFailureReport();
    }

    @Test
    void shareBookingsStep_createsShareBookingAndInvite() throws Exception {     
        UUID bookingId = UUID.randomUUID();

        MigrationRecord rec = new MigrationRecord();
        rec.setArchiveId("ARCH123");
        rec.setRecordingGroupKey("REF5|john|doe|2024-01-01"); 
        rec.setBookingId(bookingId);

        when(migrationRecordService.findShareableOrigs()).thenReturn(List.of(rec));

        Map<String, List<String[]>> channelUsersMap = Map.of(
            "REF5.john.doe.2024-01-01",
            List.<String[]>of(new String[] { "john.doe", "john.doe@test.com" })
        );
        when(cacheService.getAllChannelReferences()).thenReturn(channelUsersMap);

        BookingDTO booking = createTestBooking();
        when(bookingService.findById(bookingId)).thenReturn(booking);

        PostMigratedItemGroup migratedItem = mock(PostMigratedItemGroup.class);
        when(entityCreationService.createShareBookingAndInviteIfNotExists(
            eq(booking), eq("john.doe@test.com"), eq("john"), eq("doe")
        )).thenReturn(migratedItem);

        Tasklet tasklet = getTaskletFromStep(config.createShareBookingsStep(postMigrationWriter));
        RepeatStatus result = tasklet.execute(stepContribution, chunkContext);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(entityCreationService).createShareBookingAndInviteIfNotExists(
            eq(booking), eq("john.doe@test.com"), eq("john"), eq("doe")
        );
        verify(postMigrationWriter).write(any()); 
        verify(migrationTrackerService).writeNewUserReport();
        verify(migrationTrackerService).writeShareBookingsReport();
        verify(migrationTrackerService).writeShareInviteFailureReport();
    }

    @Test
    void buildClosedCaseDTO_setsAllFields() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod("buildClosedCaseDTO", CaseDTO.class);
        method.setAccessible(true);

        CaseDTO caseDTO = createTestCaseWithParticipant("REF7", UUID.randomUUID(), "Test", "User");

        CreateCaseDTO result = (CreateCaseDTO) method.invoke(config, caseDTO);

        assertThat(result.getReference()).isEqualTo("REF7");
        assertThat(result.getOrigin()).isEqualTo(RecordingOrigin.VODAFONE);
        assertThat(result.getState()).isEqualTo(CaseState.CLOSED);
        assertThat(result.getClosedAt()).isInstanceOf(Timestamp.class);
        assertThat(result.getParticipants()).hasSize(1);
    }

    @Test
    void mapParticipant_mapsFieldsCorrectly() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod("mapParticipant", ParticipantDTO.class);
        method.setAccessible(true);

        ParticipantDTO participant = new ParticipantDTO();
        participant.setId(UUID.randomUUID());
        participant.setFirstName("Alice");
        participant.setLastName("Johnson");
        participant.setParticipantType(ParticipantType.WITNESS);

        CreateParticipantDTO result = (CreateParticipantDTO) method.invoke(config, participant);

        assertThat(result.getFirstName()).isEqualTo("Alice");
        assertThat(result.getLastName()).isEqualTo("Johnson");
        assertThat(result.getParticipantType()).isEqualTo(ParticipantType.WITNESS);
    }

    @Test
    void hasMatchingChannelUser_returnsTrueWhenMatch() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod(
            "hasMatchingChannelUser",
            String.class,
            Map.class
        );
        method.setAccessible(true);

        Map<String, List<String[]>> channelUsersMap = Map.of(
            "test.ref", new ArrayList<>() {
                {
                    add(new String[]{"user"});
                    add(new String[]{"user"});
                }
            }
        );

        boolean result = (boolean) method.invoke(config, "REF", channelUsersMap);

        assertThat(result).isTrue();
    }

    @Test
    void fetchVodafoneCases_returnsCasesFromService() throws Exception {
        Method method = PostMigrationJobConfig.class.getDeclaredMethod("fetchVodafoneCases");
        method.setAccessible(true);

        List<CaseDTO> expectedCases = List.of(createTestCase("REF8", UUID.randomUUID()));
        when(caseService.getCasesByOrigin(RecordingOrigin.VODAFONE)).thenReturn(expectedCases);

        @SuppressWarnings("unchecked")
        List<CaseDTO> result = (List<CaseDTO>) method.invoke(config);

        assertThat(result).isEqualTo(expectedCases);
        verify(loggingService).logInfo("Found %d Vodafone-origin cases.", 1);
    }

    private Tasklet getTaskletFromStep(Step step) throws Exception {
        Field taskletField = step.getClass().getDeclaredField("tasklet");
        taskletField.setAccessible(true);
        return (Tasklet) taskletField.get(step);
    }

    private CaseDTO createTestCase(String reference, UUID id) {
        CaseDTO caseDTO = new CaseDTO();
        caseDTO.setId(id);
        caseDTO.setReference(reference);
        caseDTO.setOrigin(RecordingOrigin.VODAFONE);
        caseDTO.setTest(false);
        caseDTO.setParticipants(new ArrayList<>());
        caseDTO.setState(CaseState.OPEN);

        CourtDTO court = new CourtDTO();
        court.setId(UUID.randomUUID());
        caseDTO.setCourt(court);

        return caseDTO;
    }

    private CaseDTO createTestCaseWithParticipant(String reference, UUID id, String firstName, String lastName) {

        ParticipantDTO participant = new ParticipantDTO();
        participant.setId(UUID.randomUUID());
        participant.setFirstName(firstName);
        participant.setLastName(lastName);
        participant.setParticipantType(ParticipantType.WITNESS);

        CaseDTO caseDTO = createTestCase(reference, id);
        caseDTO.setParticipants(List.of(participant));
        return caseDTO;
    }

    private BookingDTO createTestBooking() {
        ParticipantDTO participant = new ParticipantDTO();
        participant.setFirstName("John");
        participant.setLastName("Doe");
        participant.setParticipantType(ParticipantType.WITNESS);
        BookingDTO booking = new BookingDTO();
        booking.setParticipants(List.of(participant));
        return booking;
    }
}
