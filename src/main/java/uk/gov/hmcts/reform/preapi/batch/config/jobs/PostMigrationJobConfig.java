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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import uk.gov.hmcts.reform.preapi.batch.application.services.migration.EntityCreationService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.writer.PostMigrationWriter;
import uk.gov.hmcts.reform.preapi.batch.config.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.batch.config.steps.CoreStepsConfig;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.ParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final CaseService caseService;
    private final BookingService bookingService;

    public PostMigrationJobConfig(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        CoreStepsConfig coreSteps,
        BatchConfiguration batchConfig,
        LoggingService loggingService,
        InMemoryCacheService cacheService,
        EntityCreationService entityCreationService,
        CaseService caseService,
        BookingService bookingService
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.coreSteps = coreSteps;
        this.loggingService = loggingService;
        this.cacheService = cacheService;
        this.entityCreationService = entityCreationService;
        this.caseService = caseService;
        this.bookingService = bookingService;
    }

    @Bean
    public Job postMigrationJob(
        @Qualifier("createRobotUserSignInStep") Step createRobotUserSignInStep,
        @Qualifier("createChannelUserStep") Step createChannelUserStep,
        @Qualifier("createMarkCasesClosedStep") Step createMarkCasesClosedStep,
        @Qualifier("createPreProcessStep") Step createPreProcessStep,
         @Qualifier("createShareBookingsStep") Step createShareBookingsStep
    ) {
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

                vodafoneCases.forEach(caseDTO ->
                    processCase(caseDTO, channelUsersMap, closed, skipped, dryRun)
                );

                loggingService.logInfo("Case closure summary — Total: %d, Closed: %d, Skipped: %d",
                    vodafoneCases.size(), closed.get(), skipped.get());

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

                List<CaseDTO> vodafoneCases = fetchVodafoneCases(); 
                Map<String, List<String[]>> channelUsersMap = cacheService.getAllChannelReferences();

                List<PostMigratedItemGroup> migratedItems = new ArrayList<>();

                for (CaseDTO caseDTO : vodafoneCases) {
                    loggingService.logDebug("========================================================");

                    List<String[]> matchedUsers = channelUsersMap.entrySet().stream()
                        .filter(entry -> entry.getKey().contains(caseDTO.getReference())
                            && caseDTO.getParticipants().stream().anyMatch(p ->
                                entry.getKey().toLowerCase().contains(Optional.ofNullable(p.getFirstName())
                                    .orElse("").toLowerCase())
                                || entry.getKey().toLowerCase().contains(Optional.ofNullable(p.getLastName())
                                .orElse("").toLowerCase())
                            )
                        )
                        .flatMap(entry -> entry.getValue().stream())
                        .toList();

                    List<BookingDTO> bookings = bookingService
                        .findAllByCaseId(caseDTO.getId(), Pageable.unpaged())
                        .getContent();

                    if (bookings.isEmpty()) {
                        loggingService.logWarning("No bookings found for case %s (%s)", 
                            caseDTO.getReference(), caseDTO.getId());
                    } else {
                        loggingService.logInfo("Successfully fetched %d bookings for case %s", bookings.size(), 
                            caseDTO.getReference(), caseDTO.getParticipants());
                    }

                    for (BookingDTO booking : bookings) {
                        var participants = booking.getParticipants();
                        participants.forEach(participant -> loggingService.logDebug(
                            "Booking participant: %s , first name: %s, last name: %s",
                            participant.getParticipantType(), participant.getFirstName(), participant.getLastName()));

                        for (String[] user : matchedUsers) {
                            loggingService.logDebug("user:: %s", Arrays.toString(user));
                            String email = user[1];
                            
                            String fullName = user[0];
                            String[] nameParts = fullName.split("\\.");
                            String firstName = nameParts.length > 0 ? nameParts[0] : "Unknown";
                            String lastName = nameParts.length > 1 ? nameParts[1] : "Unknown";                  

                            if (dryRun) {
                                loggingService.logInfo("[DRY RUN] Would invite and share booking with %s", email);
                                continue;
                            }
                            loggingService.logDebug("TEST");
                            var result = entityCreationService.createShareBookingAndInviteIfNotExists(
                                booking, email, firstName, lastName);
                            if (result != null) {
                                migratedItems.add(result);
                                loggingService.logDebug("✅ MigratedItemGroup added for user: %s", email);

                            }
                            loggingService.logDebug("TEST - result: %s", result);
                        }
                    }
                }

                loggingService.logInfo("MigratedItems size before writing: %d", migratedItems.size());
                postMigrationWriter.write(new Chunk<>(migratedItems));
                loggingService.logInfo("Share booking creation complete. Total created: %d", migratedItems.size());

                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    

    private List<CaseDTO> fetchVodafoneCases() {
        List<CaseDTO> cases = caseService.getCasesByOrigin(RecordingOrigin.VODAFONE);
        loggingService.logInfo("Found %d Vodafone-origin cases.", cases.size());
        return cases;
    }

    private void processCase(CaseDTO caseDTO, Map<String, List<String[]>> channelUsersMap,
        AtomicInteger closed, AtomicInteger skipped, boolean dryRun) {
        String reference = caseDTO.getReference();
        loggingService.logInfo("===== Evaluating case: %s", reference);

        if (!hasMatchingChannelUser(reference, channelUsersMap)) {
            loggingService.logDebug(
                "Case %s does not have matching channel user entry — attempting to close.",
                                    reference);
            try {
                if (!dryRun) {
                    caseService.upsert(buildClosedCaseDTO(caseDTO));
                    loggingService.logInfo("Successfully closed Vodafone case: %s", reference);
                } else {
                    loggingService.logInfo("[DRY RUN] Would close Vodafone case: %s", reference);
                }

                closed.incrementAndGet();
            } catch (Exception e) {
                loggingService.logError("Failed to close case %s: %s", reference, e.getMessage());
                skipped.incrementAndGet();
            }
        } else {
            loggingService.logInfo("Skipping case %s — matching channel user data found.", reference);
            skipped.incrementAndGet();
        }
    }

    private boolean hasMatchingChannelUser(String reference, Map<String, List<String[]>> channelUsersMap) {
        return channelUsersMap.keySet().stream()
            .anyMatch(k -> k.toLowerCase().contains(reference.toLowerCase()));
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
            loggingService.logInfo("Mapping %d participant(s) for case: %s", caseDTO.getParticipants().size(),
                caseDTO.getReference());
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


}
