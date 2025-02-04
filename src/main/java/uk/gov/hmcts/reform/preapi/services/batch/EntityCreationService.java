package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EntityCreationService {

    private static final String DEFAULT_NAME = "Unknown";
    private static final String REDIS_USER_KEY_PREFIX = "batch-preprocessor:user:";
    private final DataTransformationService transformationService;
    private final RedisService redisService;
    private final UserRepository userRepository;
    private final PortalAccessRepository portalAccessRepository;


    @Autowired
    public EntityCreationService(
        DataTransformationService transformationService,
        RedisService redisService,
        UserRepository userRepository,
        PortalAccessRepository portalAccessRepository
    ) {
        this.transformationService = transformationService;
        this.redisService = redisService;
        this.userRepository = userRepository;
        this.portalAccessRepository = portalAccessRepository;
    }

    // =========================
    // Entity Creation Methods
    // =========================

    /**
     * Creates a new Case entity from cleansed data.
     * @param cleansedData The cleansed data containing case details.
     * @return The created Case entity.
     */
    public Case createCase(CleansedData cleansedData) {
        Case aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(cleansedData.getCourt());
        aCase.setReference(cleansedData.getCaseReference());
        aCase.setTest(cleansedData.isTest());
        aCase.setCreatedAt(cleansedData.getRecordingTimestamp());
        aCase.setState(cleansedData.getState());
        return aCase;
    }

    /**
     * Creates a new Booking entity from cleansed data and associates it with a Case.
     * @param cleansedData The cleansed data containing booking details.
     * @param acase The Case entity to associate with the booking.
     * @return The created Booking entity.
     */
    public Booking createBooking(CleansedData cleansedData, Case acase) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(acase);
        booking.setScheduledFor(cleansedData.getRecordingTimestamp());
        booking.setCreatedAt(cleansedData.getRecordingTimestamp());
        // booking.setParticipants(acase.getParticipants().stream().map(p -> {
        //     var createParticipant = new Participant();
        //     createParticipant.setId(p.getId());
        //     createParticipant.setFirstName(p.getFirstName());
        //     createParticipant.setLastName(p.getLastName());
        //     createParticipant.setParticipantType(p.getParticipantType());
        //     return createParticipant;
        // }).collect(Collectors.toSet()));
        return booking;
    }

    /**
     * Creates a new CaptureSession entity and associates it with a Booking.
     * @param cleansedData The cleansed data containing session details.
     * @param booking The Booking entity to associate with the session.
     * @return The created CaptureSession entity.
     */
    public CaptureSession createCaptureSession(CleansedData cleansedData, Booking booking) {
        CaptureSession captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        captureSession.setOrigin(RecordingOrigin.VODAFONE);
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        return captureSession;
    }

    /**
     * Creates a new Recording entity and associates it with a CaptureSession.
     * @param cleansedData The cleansed data containing recording details.
     * @param captureSession The CaptureSession entity to associate with the recording.
     * @param filename The filename of the recording.
     * @return The created Recording entity.
     */
    public Recording createRecording(CleansedData cleansedData, CaptureSession captureSession, String filename) {
        Recording recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        recording.setDuration(cleansedData.getDuration());
        recording.setFilename(filename);
        recording.setCreatedAt(cleansedData.getRecordingTimestamp());
        recording.setVersion(cleansedData.getRecordingVersionNumber());
        return recording;
    }

    /**
     * Creates a set of Participant entities for a Case.
     * @param cleansedData The cleansed data containing participant details.
     * @param acase The Case entity to associate with the participants.
     * @return A set of created Participant entities.
     */
    public Set<Participant> createParticipants(CleansedData cleansedData, Case acase) {
        Set<Participant> participants = new HashSet<>();
        participants.add(createParticipant(ParticipantType.WITNESS, acase, cleansedData.getWitnessFirstName(), ""));
        participants.add(createParticipant(ParticipantType.DEFENDANT, acase, "", cleansedData.getDefendantLastName()));
        // acase.setParticipants(participants);
        return participants;
    }

    /**
     * Creates a new Participant entity.
     * @param type The type of participant (e.g., WITNESS, DEFENDANT).
     * @param acase The Case entity to associate with the participant.
     * @param firstName The first name of the participant.
     * @param lastName The last name of the participant.
     * @return The created Participant entity.
     */
    private Participant createParticipant(ParticipantType type, Case acase, String firstName, String lastName) {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setParticipantType(type);
        participant.setCaseId(acase);
        participant.setFirstName(firstName != null ? firstName : DEFAULT_NAME);
        participant.setLastName(lastName != null ? lastName : DEFAULT_NAME);
        return participant;
    }

    /**
     * Creates ShareBooking entities and associated User entities from cleansed data.
     * @param cleansedData The cleansed data containing sharing details.
     * @param booking The Booking entity to associate with the share bookings.
     * @return A list containing the created ShareBooking and User entities, or null if inputs are invalid.
     */
    public List<Object> createShareBookings(CleansedData cleansedData, Booking booking) {
        if (cleansedData == null || booking == null) {
            return null;
        }
        
        List<Object> results = new ArrayList<>();
        List<ShareBooking> shareBookings = new ArrayList<>();
        List<User> sharedWithUsers = new ArrayList<>();

        for (Map<String, String> contactInfo : cleansedData.getShareBookingContacts()) {
            User user = getOrCreateUser(contactInfo);
            if (user == null) {
                continue;  
            }
            ShareBooking shareBooking = new ShareBooking();
            shareBooking.setId(UUID.randomUUID());
            shareBooking.setBooking(booking);
            shareBooking.setSharedBy(user);
            shareBooking.setSharedWith(user);

            sharedWithUsers.add(user);
            shareBookings.add(shareBooking);
        }
        if (shareBookings.isEmpty() || sharedWithUsers.isEmpty()) {
            // Logger.getAnonymousLogger().info("No share bookings or users were created");
            return null;
        }
        results.add(shareBookings);
        results.add(sharedWithUsers);
        return results.isEmpty() ? null : results;
    }

    /**
     * Gets or creates a User entity based on email.
     * @param contactInfo A map containing user contact info.
     * @return The retrieved or created User entity, or null if the email is missing.
     */
    public User getOrCreateUser(Map<String, String> contactInfo) {
        String email = contactInfo.get("email");
        if (email == null) {
            return null;
        }

        String redisUserKey = REDIS_USER_KEY_PREFIX + email;
        String userId = redisService.getValue(redisUserKey, String.class);

        if (userId != null) {
            return userRepository.findById(UUID.fromString(userId)).orElse(null);
        } else {
            // Logger.getAnonymousLogger().info("Creating new user ");
            User newUser = new User();
            newUser.setId(UUID.randomUUID());
            newUser.setFirstName(contactInfo.getOrDefault("firstName", "Unknown"));
            newUser.setLastName(contactInfo.getOrDefault("lastName", "Unknown"));
            newUser.setEmail(email);

            userRepository.saveAndFlush(newUser);

            PortalAccess portalUser = new PortalAccess();
            portalUser.setUser(newUser);
            portalUser.setStatus(AccessStatus.INVITATION_SENT);
            portalUser.setInvitedAt(Timestamp.from(Instant.now()));
            portalAccessRepository.save(portalUser);
            
            redisService.saveValue(redisUserKey, newUser.getId().toString());
            return newUser;
        }
    }

}
