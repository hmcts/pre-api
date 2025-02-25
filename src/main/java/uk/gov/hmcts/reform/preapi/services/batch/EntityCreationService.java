
package uk.gov.hmcts.reform.preapi.services.batch;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.config.batch.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class EntityCreationService {
    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    static final String DEFAULT_NAME = "Unknown";
    static final String REDIS_USER_KEY_PREFIX = "vf:user:";
    static final String REDIS_BOOKING_FIELD = "bookingField";
    static final String REDIS_CAPTURE_SESSION_FIELD = "captureSessionField";
    static final String REDIS_RECORDING_FIELD = "recordingField";
    static final String REDIS_SHARE_BOOKING_FIELD = "vf:shareBooking:";

    @Value("${vodafone-user-email}")
    private String vodafoneUserEmail;

    private final RedisService redisService;
    private final UserService userService;

    // =========================
    // Entity Creation Methods
    // =========================
    public CreateCaseDTO createCase(CleansedData cleansedData) {
        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(UUID.randomUUID());
        caseDTO.setCourtId(cleansedData.getCourt().getId());
        caseDTO.setReference(cleansedData.getCaseReference());
        caseDTO.setParticipants(createParticipants(cleansedData));
        caseDTO.setTest(false);
        caseDTO.setState(CaseState.OPEN);
        caseDTO.setClosedAt(null);
        return caseDTO;
    }

    public CreateBookingDTO createBooking(CleansedData cleansedData, CreateCaseDTO acase, String redisKey) {
        var bookingDTO = new CreateBookingDTO();
        bookingDTO.setId(UUID.randomUUID());
        bookingDTO.setCaseId(acase.getId());
        bookingDTO.setScheduledFor(cleansedData.getRecordingTimestamp());
        bookingDTO.setParticipants(acase.getParticipants());

        redisService.saveHashValue(redisKey, REDIS_BOOKING_FIELD, bookingDTO);
        return bookingDTO;
    }

    public CreateCaptureSessionDTO createCaptureSession(
        CleansedData cleansedData, 
        CreateBookingDTO booking, 
        String redisKey
    ) {
        var vodafoneUser =  getUserByEmail(vodafoneUserEmail);

        var captureSessionDTO = new CaptureSessionDTO();
        captureSessionDTO.setId(UUID.randomUUID());
        captureSessionDTO.setBookingId(booking.getId());
        captureSessionDTO.setStartedAt(cleansedData.getRecordingTimestamp());
        captureSessionDTO.setStartedByUserId(vodafoneUser);
        captureSessionDTO.setFinishedAt(cleansedData.getFinishedAt());
        captureSessionDTO.setFinishedByUserId(vodafoneUser);
        captureSessionDTO.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSessionDTO.setCaseState(CaseState.OPEN);
        captureSessionDTO.setOrigin(RecordingOrigin.VODAFONE);

        redisService.saveHashValue(redisKey, REDIS_CAPTURE_SESSION_FIELD, captureSessionDTO);
        return captureSessionDTO;
    }

    public CreateRecordingDTO createRecording(
        String redisKey,
        CleansedData cleansedData, 
        CreateCaptureSessionDTO captureSession
        ) {
        var recordingDTO = new CreateRecordingDTO();
        recordingDTO.setId(UUID.randomUUID());
        recordingDTO.setCaptureSessionId(captureSession.getId());
        recordingDTO.setDuration(cleansedData.getDuration());
        recordingDTO.setFilename(cleansedData.getFileName());
        recordingDTO.setEditInstructions(null);
        recordingDTO.setVersion(cleansedData.getRecordingVersionNumber());

        String existingMetadata = redisService.getHashValue(redisKey, "recordingMetadata", String.class);
        UUID parentRecordingId = null;
        if (existingMetadata != null) {
            String[] parts = existingMetadata.split(":");
            parentRecordingId = UUID.fromString(parts[0]);  
        }

        if (recordingDTO.getVersion() > 1 && parentRecordingId != null) {
            recordingDTO.setParentRecordingId(parentRecordingId);
        } 

        String recordingMetadata = recordingDTO.getId().toString() + ":" + recordingDTO.getVersion();
        redisService.saveHashValue(redisKey, "recordingMetadata", recordingMetadata);

        return recordingDTO;
    }

    public Set<CreateParticipantDTO> createParticipants(CleansedData cleansedData) {
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
        participantDTO.setFirstName(firstName != null ? firstName : DEFAULT_NAME);
        participantDTO.setLastName(lastName != null ? lastName : DEFAULT_NAME);
        return participantDTO;
    }

    public List<Object> createShareBookings(CleansedData cleansedData, CreateBookingDTO booking) {
        if (cleansedData == null || booking == null) {
            return null;
        }
        
        List<Object> results = new ArrayList<>();
        List<CreateShareBookingDTO> shareBookings = new ArrayList<>();
        List<CreateInviteDTO> userInvites = new ArrayList<>();

        cleansedData.getShareBookingContacts().forEach(contactInfo -> 
            processShareBookingContact(contactInfo, booking, shareBookings, userInvites)
        );

        if (shareBookings.isEmpty()) {
            return null;
        }

        results.add(shareBookings);
        results.add(userInvites);
        return results.isEmpty() ? null : results;
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
            String existingUserId = getUserIdFromRedis(email);
            String vodafoneUser = getUserIdFromRedis(vodafoneUserEmail);

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
                redisService.saveHashValue(REDIS_USER_KEY_PREFIX, email, sharedWith.getId().toString());    
            }
            
            String redisBookingKey = REDIS_SHARE_BOOKING_FIELD + booking.getId().toString();
            String existingSharedWith = redisService.getHashValue(
                redisBookingKey, 
                sharedWith.getId().toString(), 
                String.class
            );

            if (existingSharedWith != null && existingSharedWith.equals(sharedWith.getId().toString())) {
                return; 
            }
            
            CreateUserDTO sharedBy = getUserById(vodafoneUser);
            redisService.saveHashValue(redisBookingKey, sharedWith.getId().toString(), sharedWith.getId().toString());
            shareBookings.add(createShareBooking(booking, sharedWith, sharedBy));
        } catch (Exception e) {
            logger.error("Failed to create share booking: " + e.getMessage());
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

    public String getUserIdFromRedis(String email) {
        return redisService.getHashValue(REDIS_USER_KEY_PREFIX, email, String.class);
    }

    public CreateUserDTO getUserById(String userId) {
        var user = userService.findById(UUID.fromString(userId));
        CreateUserDTO userDTO = createUser(user.getFirstName(), user.getLastName(), user.getEmail(), user.getId()); 
        return userDTO;
    }

    public UUID getUserByEmail(String email) {
        try {
            return userService.findByEmail(email).getUser().getId();
        } catch (Exception e) {
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
