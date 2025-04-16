package uk.gov.hmcts.reform.preapi.batch.application.services.migration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.entities.ProcessedRecording;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntityCreationService {
    protected static final String BOOKING_FIELD = "bookingField";
    protected static final String CAPTURE_SESSION_FIELD = "captureSessionField";
    // TODO remove unused constant ?
    private static final String RECORDING_FIELD = "recordingField";
    private static final String SHARE_BOOKING_FIELD = "vf:shareBooking:";

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
        var bookingDTO = new CreateBookingDTO();
        bookingDTO.setId(UUID.randomUUID());
        bookingDTO.setCaseId(aCase.getId());
        bookingDTO.setScheduledFor(cleansedData.getRecordingTimestamp());
        bookingDTO.setParticipants(aCase.getParticipants());

        cacheService.saveHashValue(key, BOOKING_FIELD, bookingDTO);
        return bookingDTO;
    }

    public CreateCaptureSessionDTO createCaptureSession(
        ProcessedRecording cleansedData,
        CreateBookingDTO booking,
        String key
    ) {
        var vodafoneUser = getUserByEmail(vodafoneUserEmail);

        var captureSessionDTO = new CreateCaptureSessionDTO();
        captureSessionDTO.setId(UUID.randomUUID());
        captureSessionDTO.setBookingId(booking.getId());
        captureSessionDTO.setStartedAt(cleansedData.getRecordingTimestamp());
        captureSessionDTO.setStartedByUserId(vodafoneUser);
        captureSessionDTO.setFinishedAt(cleansedData.getFinishedAt());
        captureSessionDTO.setFinishedByUserId(vodafoneUser);
        captureSessionDTO.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSessionDTO.setOrigin(RecordingOrigin.VODAFONE);

        cacheService.saveHashValue(key, CAPTURE_SESSION_FIELD, captureSessionDTO);
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
        recordingDTO.setFilename(cleansedData.getFileName());
        recordingDTO.setEditInstructions(null);
        recordingDTO.setVersion(cleansedData.getRecordingVersionNumber());

        String existingMetadata = cacheService.getHashValue(key, "recordingMetadata", String.class);
        UUID parentRecordingId = null;
        if (existingMetadata != null) {
            String[] parts = existingMetadata.split(":");
            parentRecordingId = UUID.fromString(parts[0]);
        }

        if (recordingDTO.getVersion() > 1 && parentRecordingId != null) {
            recordingDTO.setParentRecordingId(parentRecordingId);
        }

        String recordingMetadata = recordingDTO.getId().toString() + ":" + recordingDTO.getVersion();
        cacheService.saveHashValue(key, "recordingMetadata", recordingMetadata);

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

    public List<Object> createShareBookings(ProcessedRecording cleansedData, CreateBookingDTO booking) {
        if (cleansedData == null || booking == null) {
            return Collections.emptyList();
        }

        List<Object> results = new ArrayList<>();
        List<CreateShareBookingDTO> shareBookings = new ArrayList<>();
        List<CreateInviteDTO> userInvites = new ArrayList<>();

        cleansedData.getShareBookingContacts()
            .forEach(contactInfo ->
                         processShareBookingContact(
                             contactInfo,
                             booking,
                             shareBookings,
                             userInvites));

        if (shareBookings.isEmpty()) {
            return Collections.emptyList();
        }

        results.add(shareBookings);
        results.add(userInvites);
        return results;
    }

    private void processShareBookingContact(
        Map<String, String> contactInfo,
        CreateBookingDTO booking,
        List<CreateShareBookingDTO> shareBookings,
        List<CreateInviteDTO> userInvites
    ) {

        String email = contactInfo.get("email").toLowerCase();
        String firstName = contactInfo.getOrDefault("firstName", "Unknown");
        String lastName = contactInfo.getOrDefault("lastName", "Unknown");

        try {
            String existingUserId = getUserIdFromCache(email);
            String vodafoneUser = getUserIdFromCache(vodafoneUserEmail);

            CreateUserDTO sharedWith;
            CreateInviteDTO inviteDTO;

            if (existingUserId != null) {
                sharedWith = new CreateUserDTO();
                sharedWith.setId(UUID.fromString(existingUserId));
                sharedWith.setEmail(email);
            } else {
                sharedWith = createUser(firstName, lastName, email, UUID.randomUUID());
                inviteDTO = createInvite(sharedWith);
                userInvites.add(inviteDTO);
                cacheService.saveUser(email, sharedWith.getId());
            }

            String existingSharedWith = cacheService.generateCacheKey(
                "migration",
                "share-booking",
                booking.getId().toString(),
                sharedWith.getId().toString()
            );


            if (cacheService.getShareBooking(existingSharedWith).isPresent()) {
                return; 
            }
           

            CreateUserDTO sharedBy = getUserById(vodafoneUser);
            var shareBookingDTO = createShareBooking(booking, sharedWith, sharedBy);
            shareBookings.add(shareBookingDTO);
            cacheService.saveShareBooking(existingSharedWith, shareBookingDTO);
        } catch (Exception e) {
            loggingService.logError("Failed to create share booking: %s - %s", e.getMessage(), e);
        }
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
}
