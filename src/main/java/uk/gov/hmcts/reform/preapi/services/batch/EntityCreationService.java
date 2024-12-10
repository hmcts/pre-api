package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.entities.batch.CleansedData;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EntityCreationService {

    private final DataTransformationService transformationService;
    private final RedisService redisService;
    private final UserRepository userRepository;

    @Autowired
    public EntityCreationService(
        DataTransformationService transformationService,
        RedisService redisService,
        UserRepository userRepository
    ) {
        this.transformationService = transformationService;
        this.redisService = redisService;
        this.userRepository = userRepository;
    }

    public Case createCase(CleansedData cleansedData) {
        Case aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(cleansedData.getCourt());
        aCase.setReference(transformationService.buildCaseReference(cleansedData));
        aCase.setTest(cleansedData.isTest());
        aCase.setCreatedAt(cleansedData.getRecordingTimestamp());
        aCase.setState(cleansedData.getState());
        return aCase;
    }

    public Booking createBooking(CleansedData cleansedData, Case acase) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(acase);
        booking.setScheduledFor(cleansedData.getRecordingTimestamp());
        booking.setCreatedAt(cleansedData.getRecordingTimestamp());
        return booking;
    }

    public CaptureSession createCaptureSession(CleansedData cleansedData, Booking booking) {
        CaptureSession captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        captureSession.setOrigin(RecordingOrigin.VODAFONE);
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        return captureSession;
    }

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

    public Set<Participant> createParticipants(CleansedData cleansedData, Case acase) {
        Set<Participant> participants = new HashSet<>();
        participants.add(createParticipant(ParticipantType.WITNESS, acase, cleansedData.getWitnessFirstName(), ""));
        participants.add(createParticipant(ParticipantType.DEFENDANT, acase, "", cleansedData.getDefendantLastName()));
        return participants;
    }

    private Participant createParticipant(ParticipantType type, Case acase, String firstName, String lastName) {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setParticipantType(type);
        participant.setCaseId(acase);
        participant.setFirstName(firstName != null ? firstName : "Unknown");
        participant.setLastName(lastName != null ? lastName : "Unknown");
        return participant;
    }

    public List<Object> createShareBookings(CleansedData cleansedData, Booking booking) {
        List<Object> results = new ArrayList<>();
        if (cleansedData == null || booking == null) {
            return null;
        }

        List<ShareBooking> shareBookings = new ArrayList<>();
        List<User> sharedWithUsers = new ArrayList<>();

        for (Map<String, String> contactInfo : cleansedData.getShareBookingContacts()) {
            User user = getOrCreateUser(contactInfo);
            ShareBooking shareBooking = new ShareBooking();
            shareBooking.setId(UUID.randomUUID());
            shareBooking.setBooking(booking);
            shareBooking.setSharedBy(user);
            shareBooking.setSharedWith(user);

            sharedWithUsers.add(user);
            shareBookings.add(shareBooking);
        }

        results.add(shareBookings);
        results.add(sharedWithUsers);
        return results.isEmpty() ? null : results;
    }

    public User getOrCreateUser(Map<String, String> contactInfo) {
        String email = contactInfo.get("email");
        if (email == null) {
            return null;
        }

        String redisUserKey = "user:" + email;
        String userId = redisService.getValue(redisUserKey, String.class);

        User user = userRepository.findById(UUID.fromString(userId)).orElse(null);
        if (userId != null) {
            return user;
        } else {
            User newUser = new User();
            newUser.setId(UUID.randomUUID());
            newUser.setFirstName(contactInfo.getOrDefault("firstName", "Unknown"));
            newUser.setLastName(contactInfo.getOrDefault("lastName", "Unknown"));
            newUser.setEmail(email);

            redisService.saveValue(redisUserKey, newUser.getId().toString());
            return newUser;
        }
    }


}
