package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.MigrationRecordService;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntityCreationService {
    private final LoggingService loggingService;
    private final InMemoryCacheService cacheService;
    private final MigrationRecordService migrationRecordService;
    private final UserService userService;

    @Value("${vodafone-user-email}")
    private String vodafoneUserEmail;

    // =========================
    // Entity Creation Methods
    // =========================
    public CreateCaseDTO createCase(ProcessedRecording cleansedData) {
        if (cleansedData == null) {
            throw new IllegalArgumentException("ProcessedRecording cannot be null");
        }
        if (cleansedData.getCourt() == null || cleansedData.getCourt().getId() == null) {
            throw new IllegalArgumentException("Court information is missing");
        }

        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(UUID.randomUUID());
        caseDTO.setCourtId(cleansedData.getCourt().getId());
        caseDTO.setReference(cleansedData.getCaseReference());
        caseDTO.setParticipants(createParticipants(cleansedData));
        caseDTO.setTest(false);
        caseDTO.setState(CaseState.OPEN);
        caseDTO.setClosedAt(null);
        caseDTO.setOrigin(RecordingOrigin.VODAFONE);
        return caseDTO;
    }

    public CreateBookingDTO createBooking(ProcessedRecording cleansedData, CreateCaseDTO aCase, String key) {
        UUID bookingId;

        Optional<MigrationRecord> currentRecord = migrationRecordService.findByArchiveId(cleansedData.getArchiveId());
        String version = cleansedData.getExtractedRecordingVersion();

        if (version != null && version.equalsIgnoreCase("COPY") && currentRecord.isPresent()) {
            Optional<MigrationRecord> maybeOrig = migrationRecordService.getOrigFromCopy(currentRecord.get());
            if (maybeOrig.isPresent() && maybeOrig.get().getBookingId() != null) {
                bookingId = maybeOrig.get().getBookingId();
            } else {
                return null;
            }
        } else {
            bookingId = UUID.randomUUID();
        }

        CreateBookingDTO bookingDTO = new CreateBookingDTO();
        bookingDTO.setId(bookingId);
        bookingDTO.setCaseId(aCase.getId());
        bookingDTO.setScheduledFor(cleansedData.getRecordingTimestamp());
        Set<CreateParticipantDTO> filteredParticipants = aCase.getParticipants().stream()
            .filter(p ->
                (p.getFirstName() != null && p.getFirstName().equalsIgnoreCase(cleansedData.getWitnessFirstName()))
                || (p.getLastName() != null && p.getLastName().equalsIgnoreCase(cleansedData.getDefendantLastName()))
            )
            .collect(Collectors.toSet());

        bookingDTO.setParticipants(filteredParticipants);
        migrationRecordService.updateBookingId(cleansedData.getArchiveId(), bookingId);

        return bookingDTO;
    }

    public CreateCaptureSessionDTO createCaptureSession(ProcessedRecording cleansedData, CreateBookingDTO booking) {
        UUID captureSessionId;

        Optional<MigrationRecord> currentRecord = migrationRecordService.findByArchiveId(cleansedData.getArchiveId());
        String version = cleansedData.getExtractedRecordingVersion();

        if (version != null && version.equalsIgnoreCase("COPY") && currentRecord.isPresent()) {
            Optional<MigrationRecord> maybeOrig = migrationRecordService.getOrigFromCopy(currentRecord.get());
            if (maybeOrig.isPresent() && maybeOrig.get().getCaptureSessionId() != null) {
                captureSessionId = maybeOrig.get().getCaptureSessionId();
            } else {
                return null;
            }
        } else {
            captureSessionId = UUID.randomUUID();
        }

        var captureSessionDTO = new CreateCaptureSessionDTO();
        captureSessionDTO.setId(captureSessionId);
        captureSessionDTO.setBookingId(booking.getId());
        captureSessionDTO.setStartedAt(cleansedData.getRecordingTimestamp());

        var vodafoneUser = getUserByEmail(vodafoneUserEmail);
        captureSessionDTO.setStartedByUserId(vodafoneUser);
        captureSessionDTO.setFinishedAt(cleansedData.getFinishedAt());
        captureSessionDTO.setFinishedByUserId(vodafoneUser);
        captureSessionDTO.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSessionDTO.setOrigin(RecordingOrigin.VODAFONE);

        migrationRecordService.updateCaptureSessionId(cleansedData.getArchiveId(), captureSessionId);

        return captureSessionDTO;
    }

    public CreateRecordingDTO createRecording(ProcessedRecording cleansedData, CreateCaptureSessionDTO captureSession) {
        String version = cleansedData.getExtractedRecordingVersion();
        boolean isCopy = "COPY".equalsIgnoreCase(version);
        UUID parentRecordingId = null;

        if (isCopy) {
            Optional<MigrationRecord> currentRecordOpt = 
                migrationRecordService.findByArchiveId(cleansedData.getArchiveId());

            if (currentRecordOpt.isPresent()) {
                Optional<MigrationRecord> maybeOrig = migrationRecordService.getOrigFromCopy(currentRecordOpt.get());

                if (maybeOrig.isEmpty()) {
                    loggingService.logWarning("No ORIG found for COPY archiveId: %s", cleansedData.getArchiveId());
                    return null;
                }

                parentRecordingId = maybeOrig.get().getRecordingId();
                if (parentRecordingId == null) {
                    loggingService.logWarning("Parent ORIG found but has no recording ID (archiveId: %s)",
                                            maybeOrig.get().getArchiveId());
                    return null;
                }
            } else {
                loggingService.logWarning(
                    "No migration record found for COPY archiveId: %s", cleansedData.getArchiveId());
                return null;
            }
        }

        var recordingDTO = new CreateRecordingDTO();
        UUID recordingId = UUID.randomUUID();
        recordingDTO.setId(recordingId);
        recordingDTO.setCaptureSessionId(captureSession.getId());
        recordingDTO.setDuration(cleansedData.getDuration());
        recordingDTO.setEditInstructions(null);
        recordingDTO.setVersion(cleansedData.getRecordingVersionNumber());
        recordingDTO.setFilename(cleansedData.getFileName());

        if (isCopy) {
            recordingDTO.setParentRecordingId(parentRecordingId);
        }

        migrationRecordService.updateRecordingId(cleansedData.getArchiveId(), recordingId);

        return recordingDTO;
    }

   
    public Set<CreateParticipantDTO> createParticipants(ProcessedRecording cleansedData) {
        Set<CreateParticipantDTO> participants = new HashSet<>();

        if (cleansedData.getWitnessFirstName() != null && !cleansedData.getWitnessFirstName().trim().isEmpty()) {
            participants.add(createParticipant(ParticipantType.WITNESS, cleansedData.getWitnessFirstName(), ""));
        }

        if (cleansedData.getDefendantLastName() != null && !cleansedData.getDefendantLastName().trim().isEmpty()) {
            participants.add(createParticipant(ParticipantType.DEFENDANT, "", cleansedData.getDefendantLastName()));
        }

        return participants;
    }

    private CreateParticipantDTO createParticipant(ParticipantType type, String firstName, String lastName) {
        var participantDTO = new CreateParticipantDTO();
        participantDTO.setId(UUID.randomUUID());
        participantDTO.setParticipantType(type);
        participantDTO.setFirstName(firstName != null ? firstName : Constants.DEFAULT_NAME);
        participantDTO.setLastName(lastName != null ? lastName : Constants.DEFAULT_NAME);
        return participantDTO;
    }

    public CreateShareBookingDTO createShareBooking(
        CreateBookingDTO bookingDTO,
        CreateUserDTO sharedWith,
        CreateUserDTO sharedBy
    ) {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(bookingDTO.getId());
        shareBookingDTO.setSharedByUser(sharedBy.getId());
        shareBookingDTO.setSharedWithUser(sharedWith.getId());

        return shareBookingDTO;
    }

    public UUID getUserByEmail(String email) {
        try {
            return userService.findByEmail(email).getUser().getId();
        } catch (Exception e) {
            loggingService.logWarning("Could not find user by email: %s - %s", email, e);
            return null;
        }
    }

    public CreateUserDTO createUser(String firstName, String lastName, String email) {
        return createUser(firstName, lastName, email, UUID.randomUUID());
    }

    public CreateUserDTO createUser(String firstName, String lastName, String email, UUID id) {
        CreateUserDTO userDTO = new CreateUserDTO();
        userDTO.setId(id);
        userDTO.setFirstName(firstName);
        userDTO.setLastName(lastName);
        userDTO.setEmail(email);
        userDTO.setPortalAccess(Set.of());
        userDTO.setAppAccess(null);

        return userDTO;
    }

    public CreateInviteDTO createInvite(CreateUserDTO user) {
        var createInviteDTO = new CreateInviteDTO();
        createInviteDTO.setEmail(user.getEmail());
        createInviteDTO.setFirstName(user.getFirstName());
        createInviteDTO.setUserId(user.getId());
        createInviteDTO.setLastName(user.getLastName());

        return createInviteDTO;
    }

    public PostMigratedItemGroup createShareBookingAndInviteIfNotExists(BookingDTO booking,
                                                                        String email,
                                                                        String firstName,
                                                                        String lastName) {
        loggingService.logInfo("Creating share booking and user for %s %s %s", email, firstName, lastName);
        String lowerEmail = email.toLowerCase();

        List<CreateInviteDTO> invites = new ArrayList<>();

        String existingUserId = cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, lowerEmail, String.class);
        CreateUserDTO sharedWith;
        if (existingUserId != null) {
            sharedWith = new CreateUserDTO();
            sharedWith.setId(UUID.fromString(existingUserId));
            sharedWith.setEmail(lowerEmail);
        } else {
            sharedWith = createUser(firstName, lastName, lowerEmail, UUID.randomUUID());
            CreateInviteDTO invite = createInvite(sharedWith);
            invites.add(invite);
            cacheService.saveUser(lowerEmail, sharedWith.getId());
            loggingService.logDebug("Created new user and invite: %s (%s)", lowerEmail, sharedWith.getId());
        }

        String shareKey = cacheService.generateEntityCacheKey(
            "share-booking",
            booking.getId().toString(), sharedWith.getId().toString()
        );

        loggingService.logDebug("shareKey %s", shareKey);
        if (cacheService.getShareBooking(shareKey).isPresent()) {
            return null;
        }

        String vodafoneID = cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX,
                                                      vodafoneUserEmail.toLowerCase(),
                                                      String.class);
        CreateUserDTO sharedBy;

        if (vodafoneID != null) {
            sharedBy = new CreateUserDTO();
            sharedBy.setId(UUID.fromString(vodafoneID));
            sharedBy.setEmail(vodafoneUserEmail);
            cacheService.saveUser(lowerEmail, sharedWith.getId());
        } else {
            loggingService.logWarning("Vodafone user ID not found in cache for email: %s", vodafoneUserEmail);
            return null;
        }

        Set<CreateParticipantDTO> participants = booking.getCaseDTO().getParticipants().stream()
            .map(p -> {
                CreateParticipantDTO dto = new CreateParticipantDTO();
                dto.setId(p.getId());
                dto.setFirstName(p.getFirstName());
                dto.setLastName(p.getLastName());
                dto.setParticipantType(p.getParticipantType());
                return dto;
            }).collect(Collectors.toSet());

        CreateBookingDTO bookingDTO = new CreateBookingDTO();
        bookingDTO.setId(booking.getId());
        bookingDTO.setCaseId(booking.getCaseDTO().getId());
        bookingDTO.setScheduledFor(booking.getScheduledFor());
        bookingDTO.setParticipants(participants);

        List<CreateShareBookingDTO> shareBookings = new ArrayList<>();
        CreateShareBookingDTO shareBooking = createShareBooking(bookingDTO, sharedWith, sharedBy);
        shareBookings.add(shareBooking);
        cacheService.saveShareBooking(shareKey, shareBooking);

        PostMigratedItemGroup result = new PostMigratedItemGroup();
        result.setInvites(invites);
        result.setShareBookings(shareBookings);
        return result;
    }

    private boolean isOrigRecordingPersisted(String archiveId) {
        Optional<MigrationRecord> maybeRecord = migrationRecordService.findByArchiveId(archiveId);

        if (maybeRecord.isPresent()) {
            Optional<MigrationRecord> maybeOrig = migrationRecordService.getOrigFromCopy(maybeRecord.get());
            return maybeOrig.isPresent() && maybeOrig.get().getRecordingId() != null;
        }
        return false;
    }
}
