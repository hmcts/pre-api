package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.JobSynchronizationManager;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.processor.PostMigrationItemProcessor;
import uk.gov.hmcts.reform.preapi.batch.application.reader.PostMigrationItemReader;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService.CaseClosureReportEntry;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.PostMigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.preapi.batch.config.Constants.DATE_TIME_FORMAT;


@Configuration
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class PostMigrationJobConfig {
    public final PlatformTransactionManager transactionManager;
    private final JobRepository jobRepository;
    private final CoreStepsConfig coreSteps;
    private final LoggingService loggingService;
    private final InMemoryCacheService cacheService;
    private final MigrationTrackerService migrationTrackerService;
    private final CaseService caseService;
    private final PostMigrationItemReader postMigrationItemReader;
    private final PostMigrationItemProcessor postMigrationItemProcessor;
    private final UserService userService;
    private final PortalAccessRepository portalAccessRepository;
    private final BookingService bookingService;

    @Value("${vodafone-user-email}")
    private String vodafoneUserEmail;

    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String ENTITY_TYPE_SHARE_BOOKING = "ShareBooking";
    private static final String REASON_USER_INACTIVE_OR_DELETED = "User is inactive or deleted";
    private static final String REASON_SHARED_WITH_USER_NULL = "SharedWithUser is null";
    private static final String LOG_USER_DELETED = "User %s is deleted - skipping";
    private static final String LOG_USER_DELETED_PORTAL_ACCESS = "User %s has deleted portal access - skipping";
    private static final String LOG_USER_INACTIVE_PORTAL_ACCESS = "User %s has INACTIVE portal access - skipping";
    private static final String LOG_ERROR_CHECKING_USER_STATUS = "Error checking user status for %s: %s";

    public PostMigrationJobConfig(final JobRepository jobRepository,
                                  final PlatformTransactionManager transactionManager,
                                  final CoreStepsConfig coreSteps,
                                  final LoggingService loggingService,
                                  final InMemoryCacheService cacheService,
                                  final MigrationTrackerService migrationTrackerService,
                                  final CaseService caseService,
                                  final PostMigrationItemReader postMigrationItemReader,
                                  final PostMigrationItemProcessor postMigrationItemProcessor,
                                  final UserService userService,
                                  final PortalAccessRepository portalAccessRepository,
                                  final BookingService bookingService) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.coreSteps = coreSteps;
        this.loggingService = loggingService;
        this.cacheService = cacheService;
        this.migrationTrackerService = migrationTrackerService;
        this.caseService = caseService;
        this.postMigrationItemReader = postMigrationItemReader;
        this.postMigrationItemProcessor = postMigrationItemProcessor;
        this.userService = userService;
        this.portalAccessRepository = portalAccessRepository;
        this.bookingService = bookingService;
    }

    @Bean
    public Job postMigrationJob(@Qualifier("createRobotUserSignInStep") Step createRobotUserSignInStep,
                                @Qualifier("createChannelUserStep") Step createChannelUserStep,
                                @Qualifier("createMarkCasesClosedStep") Step createMarkCasesClosedStep,
                                @Qualifier("createPreProcessStep") Step createPreProcessStep,
                                @Qualifier("createShareBookingsStep") Step createShareBookingsStep,
                                @Qualifier("createWriteReportsStep") Step createWriteReportsStep,
                                @Qualifier("createWriteToCSVStep") Step createWriteToCSVStep) {
        return new JobBuilder("postMigrationJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(coreSteps.startLogging())
            .next(createRobotUserSignInStep)
            .next(createChannelUserStep)
            .next(createPreProcessStep)
            .next(createMarkCasesClosedStep)
            .next(createShareBookingsStep)
            .next(createWriteReportsStep)
            .build();
    }

    @Bean
    public Step createMarkCasesClosedStep() {
        return new StepBuilder("markCasesClosedStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                migrationTrackerService.startNewReportRun();

                boolean dryRun = Boolean.parseBoolean(
                    Optional.ofNullable(chunkContext.getStepContext().getJobParameters().get("dryRun"))
                        .map(Object::toString)
                        .orElse("false")
                );

                List<CaseDTO> vodafoneCases = fetchVodafoneCases();

                if (vodafoneCases.isEmpty()) {
                    loggingService.logInfo("No Vodafone-origin cases found.");
                    return RepeatStatus.FINISHED;
                }

                Map<String, List<String[]>> channelUsersMap = cacheService.getAllChannelReferences();
                loggingService.logInfo("Loaded %d channel reference keys from cache.", channelUsersMap.size());

                AtomicInteger closed = new AtomicInteger();
                AtomicInteger skipped = new AtomicInteger();

                vodafoneCases.forEach(caseDTO ->
                    processCase(caseDTO, channelUsersMap, closed, skipped, dryRun)
                );

                loggingService.logInfo("Case closure summary — Total: %d, Closed: %d, Skipped: %d",
                    vodafoneCases.size(), closed.get(), skipped.get());

                migrationTrackerService.writeCaseClosureReport();

                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    @Bean
    @StepScope
    public ItemReader<PostMigratedItemGroup> postMigrationItemReaderBean() {
        boolean dryRun = coreSteps.isDryRun();
        return postMigrationItemReader.createReader(dryRun);
    }

    @Bean
    @SuppressWarnings("PMD.CognitiveComplexity")
    public Step createShareBookingsStep(PostMigrationWriter postMigrationWriter,
                                        ItemReader<PostMigratedItemGroup> postMigrationItemReaderBean) {
        return new StepBuilder("createShareBookingsStep", jobRepository)
            .<PostMigratedItemGroup, PostMigratedItemGroup>chunk(
                BatchConfiguration.CHUNK_SIZE,
                new ResourcelessTransactionManager()
            )
            .reader(postMigrationItemReaderBean)
            .processor(postMigrationItemProcessor)
            .writer(createConditionalWriter(postMigrationWriter))
            .faultTolerant()
            .skipLimit(BatchConfiguration.SKIP_LIMIT)
            .skip(Exception.class)
            .build();
    }

    @Bean
    public Step createWriteReportsStep() {
        return new StepBuilder("writeReportsStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                migrationTrackerService.writeNewUserReport();
                migrationTrackerService.writeShareBookingsReport();
                migrationTrackerService.writeShareInviteFailureReport();
                loggingService.logInfo("Reports written successfully");
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    //=======================
    // Helpers
    //=======================

    private List<CaseDTO> fetchVodafoneCases() {
        List<CaseDTO> cases = caseService.getCasesByOrigin(RecordingOrigin.VODAFONE);
        loggingService.logInfo("Found %d Vodafone-origin cases.", cases.size());
        return cases;
    }

    private boolean hasRecentBookings(CaseDTO caseDTO) {
        if (caseDTO.getId() == null) {
            return false;
        }
        
        try {
            LocalDateTime sixMonthsAgoLocal = LocalDateTime.now().minusMonths(6);
            Timestamp sixMonthsAgo = Timestamp.from(
                sixMonthsAgoLocal.atZone(ZoneId.systemDefault()).toInstant()
            );
            
            Pageable pageable = PageRequest.of(0, 1000);
            var bookings = bookingService.findAllByCaseId(caseDTO.getId(), pageable);
            
            return bookings.getContent().stream()
                .anyMatch(booking -> 
                    booking.getScheduledFor() != null 
                    && booking.getScheduledFor().after(sixMonthsAgo)
                );
        } catch (Exception e) {
            loggingService.logWarning(
                "Error checking bookings for case %s: %s", 
                caseDTO.getReference(), e.getMessage()
            );
            return true;
        }
    }

    private void processCase(CaseDTO caseDTO, Map<String, List<String[]>> channelUsersMap,
        AtomicInteger closed, AtomicInteger skipped, boolean dryRun) {
        String reference = caseDTO.getReference();

        if (caseDTO.getState() == CaseState.CLOSED) {
            loggingService.logInfo("Skipping case %s — already closed.", reference);
            skipped.incrementAndGet();
            migrationTrackerService.addCaseClosureEntry(new CaseClosureReportEntry(
                caseDTO.getId() != null ? caseDTO.getId().toString() : "",
                reference,
                "ALREADY_CLOSED",
                "Case already in CLOSED state"
            ));
            return;
        }

        // Check for recent bookings (less than 6 months old)
        if (hasRecentBookings(caseDTO)) {
            loggingService.logInfo("Skipping case %s — has bookings less than 6 months old.", reference);
            skipped.incrementAndGet();
            CaseClosureReportEntry entry = new CaseClosureReportEntry(
                caseDTO.getId() != null ? caseDTO.getId().toString() : "",
                reference,
                STATUS_SKIPPED,
                "Case has bookings less than 6 months old"
            );
            migrationTrackerService.addCaseClosureEntry(entry);
            return;
        }

        if (!hasMatchingChannelUser(reference, channelUsersMap)) {
            loggingService.logDebug(
                "Case %s does not have matching channel user entry — attempting to close.",
                                    reference);
            try {
                if (!dryRun) {
                    caseService.upsert(buildClosedCaseDTO(caseDTO));
                } else {
                    loggingService.logInfo(
                        "[DRY RUN] Would close Vodafone case: %s (%s).",
                        reference, caseDTO.getId());
                }

                migrationTrackerService.addCaseClosureEntry(new CaseClosureReportEntry(
                    caseDTO.getId() != null ? caseDTO.getId().toString() : "",
                    reference,
                    dryRun ? "DRY_RUN_CLOSE" : "CLOSED",
                    ""
                ));

                closed.incrementAndGet();
            } catch (Exception e) {
                loggingService.logError(
                    "Failed to close case %s (%s): %s — %s",
                    reference, caseDTO.getId(), e.getClass().getSimpleName(), e.getMessage()
                );
                
                skipped.incrementAndGet();
                migrationTrackerService.addCaseClosureEntry(new CaseClosureReportEntry(
                    caseDTO.getId() != null ? caseDTO.getId().toString() : "",
                    reference,
                    "FAILED",
                    e.getMessage()
                ));
            }
        } else {
            loggingService.logInfo("Skipping case %s — matching channel user data found.", reference);
            skipped.incrementAndGet();
            migrationTrackerService.addCaseClosureEntry(new CaseClosureReportEntry(
                caseDTO.getId() != null ? caseDTO.getId().toString() : "",
                reference,
                STATUS_SKIPPED,
                "Matching channel user data found"
            ));
        }
    }

    private boolean hasMatchingChannelUser(String reference, Map<String, List<String[]>> channelUsersMap) {
        return channelUsersMap.keySet().stream()
            .anyMatch(k -> k.toLowerCase(Locale.UK).contains(reference.toLowerCase(Locale.UK)));
    }

    private CreateCaseDTO buildClosedCaseDTO(CaseDTO caseDTO) {
        CreateCaseDTO dto = new CreateCaseDTO();
        dto.setId(caseDTO.getId());
        dto.setCourtId(caseDTO.getCourt().getId());
        dto.setReference(caseDTO.getReference());
        dto.setOrigin(caseDTO.getOrigin());
        dto.setTest(caseDTO.isTest());
        dto.setState(CaseState.CLOSED);
        dto.setClosedAt(Timestamp.from(Instant.now()));

        if (caseDTO.getParticipants() != null) {
            Set<CreateParticipantDTO> createParticipants = caseDTO.getParticipants().stream()
                .map(this::mapParticipant)
                .collect(Collectors.toSet());
            dto.setParticipants(createParticipants);
        }

        return dto;
    }

    private CreateParticipantDTO mapParticipant(ParticipantDTO participant) {
        CreateParticipantDTO dto = new CreateParticipantDTO();
        dto.setId(participant.getId());
        dto.setFirstName(participant.getFirstName());
        dto.setLastName(participant.getLastName());
        dto.setParticipantType(participant.getParticipantType());
        return dto;
    }

    private String resolveEmailForShare(PostMigratedItemGroup item, CreateShareBookingDTO share) {
        if (item.getInvites() != null) {
            String email = item.getInvites().stream()
                .filter(invite -> invite.getUserId() != null
                    && invite.getUserId().equals(share.getSharedWithUser()))
                .map(CreateInviteDTO::getEmail)
                .findFirst()
                .orElse("");
            if (!email.isEmpty()) {
                return email;
            }
        }

        if (share.getSharedWithUser() != null) {
            try {
                var user = userService.findById(share.getSharedWithUser());
                return user.getEmail();
            } catch (NotFoundException e) {
                loggingService.logWarning(
                    "Could not find user email for ID: %s - %s", share.getSharedWithUser(), e.getMessage());
            } catch (Exception e) {
                loggingService.logWarning(
                    "Could not find user email for ID: %s - %s", share.getSharedWithUser(), e.getMessage());
            }
        }
        return "";
    }

    private ItemWriter<PostMigratedItemGroup> createConditionalWriter(PostMigrationWriter postMigrationWriter) {
        return chunk -> {
            boolean dryRun = Boolean.parseBoolean(
                Optional.ofNullable(JobSynchronizationManager.getContext())
                    .map(ctx -> ctx.getJobParameters().get("dryRun"))
                    .map(Object::toString)
                    .orElse("false")
            );

            if (dryRun) {
                loggingService.logInfo(
                    "[DRY RUN] PostMigrationWriter processing %d item(s) - skipping entity creation", chunk.size());

                for (PostMigratedItemGroup item : chunk) {
                    try {
                        loggingService.logDebug("[DRY RUN] Processing post-migration item group: %s", item);

                        if (item.getInvites() != null) {
                            for (CreateInviteDTO invite : item.getInvites()) {
                                if (invite.getUserId() != null 
                                    && !isUserActiveForMigration(invite.getUserId(), invite.getEmail())) {
                                    loggingService.logWarning(
                                        "[DRY RUN] Skipping invite for inactive/deleted user: %s", invite.getEmail());
                                    migrationTrackerService.addShareInviteFailure(
                                        new MigrationTrackerService.ShareInviteFailureEntry(
                                            "Invite",
                                            invite.getUserId().toString(),
                                            invite.getEmail(),
                                            STATUS_SKIPPED,
                                            REASON_USER_INACTIVE_OR_DELETED,
                                            DateTimeFormatter.ofPattern(DATE_TIME_FORMAT).format(LocalDateTime.now())
                                    ));
                                    continue;
                                }
                                migrationTrackerService.addInvitedUser(invite);
                            }
                        }

                        if (item.getShareBookings() != null) {
                            for (CreateShareBookingDTO share : item.getShareBookings()) {
                                String email = resolveEmailForShare(item, share);
                                String skipReason = getSkipReasonForShare(share, email);
                                if (skipReason != null) {
                                    loggingService.logWarning(
                                        "[DRY RUN] Skipping share booking: %s", skipReason);
                                    migrationTrackerService.addShareInviteFailure(
                                        new MigrationTrackerService.ShareInviteFailureEntry(
                                            ENTITY_TYPE_SHARE_BOOKING,
                                            share.getId() != null ? share.getId().toString() : "",
                                            email.isEmpty() ? "unknown" : email,
                                            STATUS_SKIPPED,
                                            skipReason,
                                            DateTimeFormatter.ofPattern(DATE_TIME_FORMAT).format(LocalDateTime.now())
                                    ));
                                    continue;
                                }
                                migrationTrackerService.addShareBooking(share);
                                migrationTrackerService.addShareBookingReport(share, email, vodafoneUserEmail);
                            }
                        }

                        loggingService.logDebug("[DRY RUN] Successfully processed post-migration item");
                    } catch (Exception e) {
                        loggingService.logError("[DRY RUN] Failed to process post-migration item: %s", e.getMessage());
                    }
                }
            } else {
                postMigrationWriter.write(chunk);
            }
        };
    }

    private String getSkipReasonForShare(CreateShareBookingDTO share, String email) {
        if (share.getSharedWithUser() == null) {
            return REASON_SHARED_WITH_USER_NULL;
        }
        if (!isUserActiveForMigration(share.getSharedWithUser(), email)) {
            return REASON_USER_INACTIVE_OR_DELETED;
        }
        return null;
    }

    private boolean isUserActiveForMigration(UUID userId, String email) {
        try {
            var user = userService.findById(userId);
            if (user.getDeletedAt() != null) {
                loggingService.logDebug(LOG_USER_DELETED, email);
                return false;
            }
            
            var portalAccess = portalAccessRepository
                .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId);
            
            if (portalAccess.isEmpty()) {
                var deletedPortalAccess = portalAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(userId);
                if (!deletedPortalAccess.isEmpty()) {
                    loggingService.logDebug(LOG_USER_DELETED_PORTAL_ACCESS, email);
                    return false;
                }
                return true;
            }
            
            if (portalAccess.get().getStatus() == AccessStatus.INACTIVE) {
                loggingService.logDebug(LOG_USER_INACTIVE_PORTAL_ACCESS, email);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            loggingService.logWarning(LOG_ERROR_CHECKING_USER_STATUS, email, e.getMessage());
            return false;
        }
    }

}
