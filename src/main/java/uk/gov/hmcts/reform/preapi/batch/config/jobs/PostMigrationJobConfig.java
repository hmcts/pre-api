package uk.gov.hmcts.reform.preapi.batch.config.jobs;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.EntityCreationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.MigrationTrackerService.CaseClosureReportEntry;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.PostMigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Configuration
public class PostMigrationJobConfig {
    public final PlatformTransactionManager transactionManager;
    private final JobRepository jobRepository;
    private final CoreStepsConfig coreSteps;
    private final LoggingService loggingService;
    private final InMemoryCacheService cacheService;
    private final EntityCreationService entityCreationService;
    private final MigrationTrackerService migrationTrackerService;
    private final MigrationRecordService migrationRecordService;
    private final CaseService caseService;
    private final BookingService bookingService;
    private final RecordingService recordingService;

    @Value("${vodafone-user-email}")
    private String vodafoneUserEmail;

    public PostMigrationJobConfig(final JobRepository jobRepository,
                                  final PlatformTransactionManager transactionManager,
                                  final CoreStepsConfig coreSteps,
                                  final LoggingService loggingService,
                                  final InMemoryCacheService cacheService,
                                  final EntityCreationService entityCreationService,
                                  final MigrationTrackerService migrationTrackerService,
                                  final MigrationRecordService migrationRecordService,
                                  final CaseService caseService,
                                  final BookingService bookingService,
                                  final RecordingService recordingService) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.coreSteps = coreSteps;
        this.loggingService = loggingService;
        this.cacheService = cacheService;
        this.entityCreationService = entityCreationService;
        this.migrationTrackerService = migrationTrackerService;
        this.migrationRecordService = migrationRecordService;
        this.caseService = caseService;
        this.bookingService = bookingService;
        this.recordingService = recordingService;
    }

    @Bean
    public Job postMigrationJob(@Qualifier("createRobotUserSignInStep") Step createRobotUserSignInStep,
                                @Qualifier("createChannelUserStep") Step createChannelUserStep,
                                @Qualifier("createMarkCasesClosedStep") Step createMarkCasesClosedStep,
                                @Qualifier("createPreProcessStep") Step createPreProcessStep,
                                @Qualifier("createShareBookingsStep") Step createShareBookingsStep,
                                @Qualifier("createWriteToCSVStep") Step createWriteToCSVStep) {
        return new JobBuilder("postMigrationJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(coreSteps.startLogging())
            .next(createRobotUserSignInStep)
            .next(createChannelUserStep)
            .next(createPreProcessStep)
            .next(createMarkCasesClosedStep)
            .next(createShareBookingsStep)
            .build();
    }

    @Bean
    public Step createMarkCasesClosedStep() {
        return new StepBuilder("markCasesClosedStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
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
                AtomicInteger recordingsFound = new AtomicInteger();
                AtomicInteger recordingsDeleted = new AtomicInteger();

                vodafoneCases.forEach(caseDTO ->
                    processCase(caseDTO, channelUsersMap, closed, skipped, dryRun, recordingsFound, recordingsDeleted)
                );

                loggingService.logInfo("Case closure summary — Total: %d, Closed: %d, Skipped: %d",
                    vodafoneCases.size(), closed.get(), skipped.get());

                loggingService.logInfo("Recording cleanup summary — Found: %d, Removed: %d",
                    recordingsFound.get(), recordingsDeleted.get());

                migrationTrackerService.writeCaseClosureReport();

                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    @Bean
    public Step createShareBookingsStep(PostMigrationWriter postMigrationWriter) {
        return new StepBuilder("createShareBookingsStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                boolean dryRun = Boolean.parseBoolean(
                    Optional.ofNullable(chunkContext.getStepContext().getJobParameters().get("dryRun"))
                        .map(Object::toString)
                        .orElse("false")
                );

                // fetch sucessful ORIGs with booking + group key
                List<MigrationRecord> shareableOrigs = migrationRecordService.findShareableOrigs();
                Map<String, List<String[]>> channelUsersMap = cacheService.getAllChannelReferences();

                List<PostMigratedItemGroup> migratedItems = new ArrayList<>();

                for (MigrationRecord orig : shareableOrigs) {
                    loggingService.logDebug("========================================================");
                    loggingService.logDebug("Processing record: archiveId=%s, groupKey=%s",
                        orig.getArchiveId(), orig.getRecordingGroupKey());

                    // find matching channel users by checking the groupKey parts
                    List<String[]> matchedUsers = channelUsersMap.entrySet().stream()
                        .filter(entry -> channelContainsAllGroupParts(orig.getRecordingGroupKey(), entry.getKey()))
                        .flatMap(entry -> entry.getValue().stream())
                        .toList();

                    if (matchedUsers.isEmpty()) {
                        loggingService.logDebug("No matching channel users found for groupKey=%s",
                            orig.getRecordingGroupKey());
                        continue;
                    }

                    if (orig.getBookingId() == null) {
                        loggingService.logWarning("Record %s has no bookingId", orig.getArchiveId());
                        continue;
                    }

                    // fetch booking by booking_id  
                    BookingDTO booking;
                    try {
                        booking = bookingService.findById(orig.getBookingId()); 
                    } catch (Exception ex) {
                        loggingService.logWarning("No booking found for record %s (bookingId=%s) — %s",
                            orig.getArchiveId(), orig.getBookingId(), ex.getMessage());
                        continue;
                    }
                    if (booking == null) {
                        loggingService.logWarning("No booking found for record %s (bookingId=%s)",
                            orig.getArchiveId(), orig.getBookingId());
                        continue;
                    }

                    var alreadySharedEmails = booking.getShares() == null ? new HashSet<String>() 
                        : booking.getShares().stream()
                        .filter(share -> share.getDeletedAt() == null && share.getSharedWithUser() != null)
                        .map(share -> share.getSharedWithUser().getEmail().toLowerCase())
                        .collect(Collectors.toCollection(HashSet::new));

                    for (String[] user : matchedUsers) {
                        String email = user[1];
                        String fullName = user[0];
                        String[] nameParts = fullName.split("\\.");
                        String firstName = nameParts.length > 0 ? nameParts[0] : "Unknown";
                        String lastName  = nameParts.length > 1 ? nameParts[1] : "Unknown";

                        String emailKey = email.toLowerCase();

                        if (alreadySharedEmails.contains(emailKey)) {
                            loggingService.logDebug("Skipping share creation for %s — already shared.", email);
                            continue;
                        }

                        if (dryRun) {
                            loggingService.logInfo("[DRY RUN] Would invite and share booking with %s", email);
                            alreadySharedEmails.add(emailKey);
                            continue;
                        }

                        var result = entityCreationService.createShareBookingAndInviteIfNotExists(
                            booking, email, firstName, lastName
                        );

                        if (result != null) {
                            migratedItems.add(result);
                            alreadySharedEmails.add(emailKey);
                            if (result.getInvites() != null) {
                                result.getInvites().forEach(migrationTrackerService::addInvitedUser);
                            }
                            if (result.getShareBookings() != null) {  
                                result.getShareBookings().forEach(shareBooking -> {
                                    migrationTrackerService.addShareBooking(shareBooking);
                                    migrationTrackerService.addShareBookingReport(
                                        shareBooking,
                                        email,
                                        Optional.ofNullable(vodafoneUserEmail).orElse("")
                                    );
                                });
                            }

                            loggingService.logDebug("MigratedItemGroup added for user: %s", email);
                        }
                    }
                }

                postMigrationWriter.write(new Chunk<>(migratedItems));
                migrationTrackerService.writeNewUserReport();
                migrationTrackerService.writeShareBookingsReport();
                migrationTrackerService.writeShareInviteFailureReport();
                loggingService.logInfo("Share booking creation complete. Total created: %d", migratedItems.size());

                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    //=======================
    // Helpers
    //=======================
    private static boolean channelContainsAllGroupParts(String recordingGroupKey, String channelName) {
        if (recordingGroupKey == null || channelName == null) {
            return false;
        }

        String lowerChannel = channelName.toLowerCase();

        for (String rawPart : recordingGroupKey.split("\\|")) {
            if (rawPart == null || rawPart.isBlank()) {
                continue;
            }

            String part = rawPart.toLowerCase().trim();

            if (part.matches("\\d{4}-\\d{2}-\\d{2}")) {
                String yymmdd =
                    part.substring(2, 4) + part.substring(5, 7) + part.substring(8, 10);

                if (lowerChannel.contains(part) || lowerChannel.contains(yymmdd)) {
                    continue; 
                }
                return false;
            }

            if (!lowerChannel.contains(part)) {
                return false;
            }
        }

        return true;
    }

    private List<CaseDTO> fetchVodafoneCases() {
        List<CaseDTO> cases = caseService.getCasesByOrigin(RecordingOrigin.VODAFONE);
        loggingService.logInfo("Found %d Vodafone-origin cases.", cases.size());
        return cases;
    }

    private void processCase(CaseDTO caseDTO, Map<String, List<String[]>> channelUsersMap,
        AtomicInteger closed, AtomicInteger skipped, boolean dryRun,
        AtomicInteger recordingsFound, AtomicInteger recordingsDeleted) {
        String reference = caseDTO.getReference();

        if (caseDTO.getState() == CaseState.CLOSED) {
            loggingService.logInfo("Skipping case %s — already closed.", reference);
            skipped.incrementAndGet();
            migrationTrackerService.addCaseClosureEntry(new CaseClosureReportEntry(
                caseDTO.getId() != null ? caseDTO.getId().toString() : "",
                reference,
                "ALREADY_CLOSED",
                0,
                0,
                "Case already in CLOSED state"
            ));
            return;
        }

        if (!hasMatchingChannelUser(reference, channelUsersMap)) {
            loggingService.logDebug(
                "Case %s does not have matching channel user entry — attempting to close.",
                                    reference);
            try {
                CleanupStats cleanupStats = deleteActiveRecordings(caseDTO, dryRun);
                recordingsFound.addAndGet(cleanupStats.found());
                recordingsDeleted.addAndGet(cleanupStats.deleted());

                if (!dryRun) {
                    caseService.upsert(buildClosedCaseDTO(caseDTO));
                    loggingService.logInfo(
                        "Closed Vodafone case: %s (%s). Removed %d recording(s).",
                        reference, caseDTO.getId(), cleanupStats.deleted());
                } else {
                    loggingService.logInfo(
                        "[DRY RUN] Would close Vodafone case: %s (%s). Would remove %d recording(s).",
                        reference, caseDTO.getId(), cleanupStats.found());
                }

                migrationTrackerService.addCaseClosureEntry(new CaseClosureReportEntry(
                    caseDTO.getId() != null ? caseDTO.getId().toString() : "",
                    reference,
                    dryRun ? "DRY_RUN_CLOSE" : "CLOSED",
                    cleanupStats.found(),
                    cleanupStats.deleted(),
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
                    0,
                    0,
                    e.getMessage()
                ));
            }
        } else {
            loggingService.logInfo("Skipping case %s — matching channel user data found.", reference);
            skipped.incrementAndGet();
            migrationTrackerService.addCaseClosureEntry(new CaseClosureReportEntry(
                caseDTO.getId() != null ? caseDTO.getId().toString() : "",
                reference,
                "SKIPPED",
                0,
                0,
                "Matching channel user data found"
            ));
        }
    }

    private boolean hasMatchingChannelUser(String reference, Map<String, List<String[]>> channelUsersMap) {
        return channelUsersMap.keySet().stream()
            .anyMatch(k -> k.toLowerCase().contains(reference.toLowerCase()));
    }

    private CleanupStats deleteActiveRecordings(CaseDTO caseDTO, boolean dryRun) {
        AtomicInteger discoveredCount = new AtomicInteger();
        AtomicInteger deletedCount = new AtomicInteger();

        bookingService.findAllByCaseId(caseDTO.getId(), Pageable.unpaged()).forEach(booking -> {
            if (booking.getCaptureSessions() == null) {
                return;
            }

            booking.getCaptureSessions().forEach(captureSession -> {
                SearchRecordings params = new SearchRecordings();
                params.setCaptureSessionId(captureSession.getId());

                var recordings = recordingService.findAll(params, false, Pageable.unpaged());
                if (recordings.isEmpty()) {
                    return;
                }

                recordings.forEach(recording -> {
                    discoveredCount.incrementAndGet();

                    if (dryRun) {
                        loggingService.logDebug(
                            "[DRY RUN] Would delete recording %s for case %s (capture session %s)",
                            recording.getId(), caseDTO.getReference(), captureSession.getId()
                        );
                    } else {
                        try {
                            recordingService.deleteById(recording.getId());
                            deletedCount.incrementAndGet();
                            loggingService.logDebug(
                                "Deleted recording %s for case %s (capture session %s)",
                                recording.getId(), caseDTO.getReference(), captureSession.getId()
                            );
                        } catch (Exception ex) {
                            loggingService.logError(
                                "Failed to delete recording %s for case %s: %s",
                                recording.getId(), caseDTO.getReference(), ex.getMessage()
                            );
                        }
                    }
                });
            });
        });

        return new CleanupStats(discoveredCount.get(), deletedCount.get());
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

    private CreateParticipantDTO mapParticipant(ParticipantDTO p) {
        CreateParticipantDTO dto = new CreateParticipantDTO();
        dto.setId(p.getId());
        dto.setFirstName(p.getFirstName());
        dto.setLastName(p.getLastName());
        dto.setParticipantType(p.getParticipantType());
        return dto;
    }

    private record CleanupStats(int found, int deleted) {
    }

}
