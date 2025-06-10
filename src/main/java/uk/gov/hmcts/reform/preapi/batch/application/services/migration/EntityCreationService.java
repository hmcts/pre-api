package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntityCreationService {
    protected static final String BOOKING_FIELD = "booking";
    protected static final String CAPTURE_SESSION_FIELD = "captureSession";

    @Value("${vodafone-user-email}")
    private String vodafoneUserEmail;

    private final LoggingService loggingService;
    private final InMemoryCacheService cacheService;
    private final UserService userService;

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
 
        String bookingKey = cacheService.generateBookingCacheKey(
            key,
            cleansedData.getExtractedRecordingVersionNumberStr()
        );
        String existingBookingId = cacheService.getHashValue(bookingKey, "id", String.class);

        var bookingDTO = new CreateBookingDTO();
        if (existingBookingId != null) {
            bookingDTO.setId(UUID.fromString(existingBookingId));
        } else {
            bookingDTO.setId(UUID.randomUUID());
            cacheService.saveHashValue(bookingKey, "id", bookingDTO.getId().toString());
        }

        bookingDTO.setCaseId(aCase.getId());
        bookingDTO.setScheduledFor(cleansedData.getRecordingTimestamp());
        bookingDTO.setParticipants(aCase.getParticipants());

        return bookingDTO;
    }

    public CreateCaptureSessionDTO createCaptureSession(
        ProcessedRecording cleansedData,
        CreateBookingDTO booking,
        String key
    ) {
        
        String sessionKey = key + ":version:" + cleansedData.getExtractedRecordingVersionNumberStr() + ":sessionId";
        String existingId = cacheService.getHashValue(sessionKey, "id", String.class);
        
        var captureSessionDTO = new CreateCaptureSessionDTO();
        
        if (existingId != null) {
            captureSessionDTO.setId(UUID.fromString(existingId));
        } else {
            captureSessionDTO.setId(UUID.randomUUID());
            cacheService.saveHashValue(sessionKey, "id", captureSessionDTO.getId().toString());
        }
        captureSessionDTO.setBookingId(booking.getId());
        captureSessionDTO.setStartedAt(cleansedData.getRecordingTimestamp());

        var vodafoneUser = getUserByEmail(vodafoneUserEmail);
        captureSessionDTO.setStartedByUserId(vodafoneUser);
        captureSessionDTO.setFinishedAt(cleansedData.getFinishedAt());
        captureSessionDTO.setFinishedByUserId(vodafoneUser);
        captureSessionDTO.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSessionDTO.setOrigin(RecordingOrigin.VODAFONE);

        return captureSessionDTO;
    }

    public CreateRecordingDTO createRecording(
        String key,
        ProcessedRecording cleansedData,
        CreateCaptureSessionDTO captureSession
    ) {
        var recordingDTO = new CreateRecordingDTO();
        recordingDTO.setId(UUID.randomUUID());
        recordingDTO.setCaptureSessionId(captureSession.getId());
        recordingDTO.setDuration(cleansedData.getDuration());
        recordingDTO.setEditInstructions(null);
        recordingDTO.setVersion(cleansedData.getRecordingVersionNumber());

        // Extract key details
        String caseRef = cleansedData.getCaseReference();
        String witness = cleansedData.getWitnessFirstName();
        String defendant = cleansedData.getDefendantLastName();
        String version = cleansedData.getExtractedRecordingVersionNumberStr();
        String versionStr = cleansedData.getExtractedRecordingVersion();
        boolean isCopy = versionStr != null && versionStr.toUpperCase().contains("COPY");

        // Cache key setup
        String versionKey = cacheService.generateCacheKey("recording", caseRef, defendant, witness);
        String archiveNameKey = String.format("archiveName:%s:%s", isCopy ? "copy" : "orig", version);
        String parentKey = String.format("parentLookup:%s", version);

        // Resolve filename
        String resolvedFilename = cacheService.getHashValue(versionKey, archiveNameKey, String.class);
        recordingDTO.setFilename(resolvedFilename != null ? resolvedFilename : cleansedData.getFileName());
        loggingService.logDebug("Resolved filename: %s", recordingDTO.getFilename());

        if (isCopy) {
            String parentIdStr = cacheService.getHashValue(versionKey, parentKey, String.class);
            loggingService.logDebug("Looking up ORIG parent with key %s → %s", parentKey, parentIdStr);

            if (parentIdStr != null) {
                recordingDTO.setParentRecordingId(UUID.fromString(parentIdStr));
            } else {
                loggingService.logWarning("No ORIG found for COPY version %d", version);
            }
        } else {
            if (!cacheService.checkHashKeyExists(versionKey, parentKey)) {
                cacheService.saveHashValue(versionKey, parentKey, recordingDTO.getId().toString());
            } else {
                loggingService.logDebug("Skipped storing ORIG ID under %s (already exists)", parentKey);
            }
        }
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

    public String getUserIdFromCache(String email) {
        return cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, email, String.class);
    }

    public CreateUserDTO getUserById(String userId) {
        var user = userService.findById(UUID.fromString(userId));
        return createUser(user.getFirstName(), user.getLastName(), user.getEmail(), user.getId());
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

    public PostMigratedItemGroup createShareBookingAndInviteIfNotExists(
        BookingDTO booking, String email, String firstName, String lastName
    ) {
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

        String shareKey = cacheService.generateCacheKey(
            "migration", "share-booking", booking.getId().toString(), sharedWith.getId().toString()
        );

        loggingService.logDebug("shareKey %s", shareKey);
        if (cacheService.getShareBooking(shareKey).isPresent()) {
            return null;
        }

        String vodafoneID = cacheService.getHashValue(Constants.CacheKeys.USERS_PREFIX, 
            vodafoneUserEmail.toLowerCase(), String.class);
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

}
