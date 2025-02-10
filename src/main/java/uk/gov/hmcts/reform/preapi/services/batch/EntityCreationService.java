
package uk.gov.hmcts.reform.preapi.services.batch;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EntityCreationService {

    private static final String DEFAULT_NAME = "Unknown";
    private static final String REDIS_USER_KEY_PREFIX = "batch-preprocessor:user:";
    private static final String REDIS_BOOKING_FIELD = "bookingField";
    private static final String REDIS_CAPTURE_SESSION_FIELD = "captureSessionField";
    private final RedisService redisService;
    private final UserService userService;


    @Autowired
    public EntityCreationService(
        RedisService redisService,
        UserRepository userRepository,
        UserService userService,
        PortalAccessRepository portalAccessRepository
    ) {
        this.redisService = redisService;
        this.userService = userService;
    }

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
        var captureSessionDTO = new CaptureSessionDTO();
        captureSessionDTO.setId(UUID.randomUUID());
        captureSessionDTO.setBookingId(booking.getId());
        captureSessionDTO.setStartedAt(cleansedData.getRecordingTimestamp());
        captureSessionDTO.setFinishedAt(cleansedData.getFinishedAt());
        captureSessionDTO.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSessionDTO.setCaseState(CaseState.OPEN);
        captureSessionDTO.setOrigin(RecordingOrigin.VODAFONE);

        redisService.saveHashValue(redisKey, REDIS_CAPTURE_SESSION_FIELD, captureSessionDTO);
        return captureSessionDTO;
    }

    public CreateRecordingDTO createRecording(CleansedData cleansedData, CreateCaptureSessionDTO captureSession) {
        var recordingDTO = new CreateRecordingDTO();
        recordingDTO.setId(UUID.randomUUID());
        recordingDTO.setCaptureSessionId(captureSession.getId());
        recordingDTO.setDuration(cleansedData.getDuration());
        recordingDTO.setFilename(cleansedData.getFileName());
        recordingDTO.setEditInstructions(null);
        recordingDTO.setVersion(cleansedData.getRecordingVersionNumber());
        return recordingDTO;
    }

    public Set<CreateParticipantDTO> createParticipants(CleansedData cleansedData) {
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS,  cleansedData.getWitnessFirstName(), ""),
            createParticipant(ParticipantType.DEFENDANT,  "", cleansedData.getDefendantLastName())
        );
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

        for (Map<String, String> contactInfo : cleansedData.getShareBookingContacts()) {
            String userId = getUserIdFromRedis(contactInfo);
            CreateUserDTO userDTO;
            CreateInviteDTO inviteDTO;
            if (userId != null) {
                userDTO = getUserFromDB(userId);
            } else {
                String firstName = contactInfo.getOrDefault("firstName", "Unknown");
                String lastName = contactInfo.getOrDefault("lastName", "Unknown");
                String email = contactInfo.get("email");
                userDTO = createUser(firstName, lastName, email, UUID.randomUUID()); 
                inviteDTO = createInvite(userDTO);
                userInvites.add(inviteDTO);
            }

            var shareBookingDTO = createShareBooking(booking, userDTO);
            shareBookings.add(shareBookingDTO);
        }

        if (shareBookings.isEmpty()) {
            return null;
        }

        results.add(shareBookings);
        results.add(userInvites);
        return results.isEmpty() ? null : results;
    }


    public CreateShareBookingDTO createShareBooking(CreateBookingDTO bookingDTO, CreateUserDTO userDTO) {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(bookingDTO.getId());
        shareBookingDTO.setSharedByUser(userDTO.getId());
        shareBookingDTO.setSharedWithUser(userDTO.getId());
        
        return shareBookingDTO;    
    }

    public String getUserIdFromRedis(Map<String, String> contactInfo) {
        String email = contactInfo.get("email");
        String redisUserKey = REDIS_USER_KEY_PREFIX + email;
        return redisService.getValue(redisUserKey, String.class);
    }

    public CreateUserDTO getUserFromDB(String userId) {
        var user = userService.findById(UUID.fromString(userId));
        CreateUserDTO userDTO = createUser(user.getFirstName(), user.getLastName(), user.getEmail(), user.getId()); 
        return userDTO;
    }


    public CreateUserDTO createUser(String firstName, String lastName, String email) {
        return createUser(firstName, lastName, email, UUID.randomUUID());
    }

    public CreateUserDTO createUser(String firstName, String lastName, String email, UUID id) {
        CreateUserDTO userDTO = new CreateUserDTO();
        userDTO.setId(id);
        userDTO.setFirstName(StringUtils.capitalize(firstName.toLowerCase()));
        userDTO.setLastName(StringUtils.capitalize(lastName.toLowerCase()));
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
