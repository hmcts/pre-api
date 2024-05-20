package uk.gov.hmcts.reform.preapi.util;

import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.Room;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;

@UtilityClass
public class HelperFactory {
    public User createDefaultTestUser() {
        return createUser("Test", "User", "example@example.com", new Timestamp(System.currentTimeMillis()), null, null);
    }

    public User createUser(String firstName,
                           String lastName,
                           String email,
                           Timestamp deletedAt,
                           @Nullable String phone,
                           @Nullable String organisation) { //NOPMD - suppressed UseObjectForClearerAPI
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setDeletedAt(deletedAt);
        user.setPhone(phone);
        user.setOrganisation(organisation);
        return user;
    }

    public Court createCourt(CourtType courtType, String name, @Nullable String locationCode) {
        Court court = new Court();
        court.setCourtType(courtType);
        court.setName(name);
        court.setLocationCode(locationCode);
        return court;
    }

    public Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    public AppAccess createAppAccess(User user,
                                     Court court,
                                     Role role,
                                     boolean active,
                                     Timestamp deletedAt,
                                     @Nullable Timestamp lastAccess, boolean isDefaultCourt) {
        AppAccess appAccess = new AppAccess();
        appAccess.setUser(user);
        appAccess.setCourt(court);
        appAccess.setRole(role);
        appAccess.setLastAccess(lastAccess);
        appAccess.setActive(active);
        appAccess.setDeletedAt(deletedAt);
        appAccess.setDefaultCourt(isDefaultCourt);
        return appAccess;
    }

    public PortalAccess createPortalAccess(User user,
                                           Timestamp lastAccess,
                                           AccessStatus status,
                                           Timestamp invitedAt,
                                           Timestamp registeredAt,
                                           Timestamp deletedAt) {
        var portalAccess = new PortalAccess();
        portalAccess.setUser(user);
        portalAccess.setLastAccess(lastAccess);
        portalAccess.setStatus(status);
        portalAccess.setInvitedAt(invitedAt);
        portalAccess.setRegisteredAt(registeredAt);
        portalAccess.setDeletedAt(deletedAt);
        return portalAccess;
    }

    public Case createCase(Court court, String reference, boolean test, Timestamp deletedAt) {
        Case testCase = new Case();
        testCase.setCourt(court);
        testCase.setReference(reference);
        testCase.setTest(test);
        testCase.setDeletedAt(deletedAt);
        return testCase;
    }

    public Booking createBooking(Case testingCase, Timestamp scheduledFor, Timestamp deletedAt) {
        return createBooking(testingCase, scheduledFor, deletedAt, null);
    }

    public Booking createBooking(Case testingCase,
                                 Timestamp scheduledFor,
                                 Timestamp deletedAt,
                                 @Nullable Set<Participant> participants) {
        Booking booking = new Booking();
        booking.setCaseId(testingCase);
        booking.setScheduledFor(scheduledFor);
        booking.setDeletedAt(deletedAt);
        booking.setParticipants(participants);
        return booking;
    }

    public CaptureSession createCaptureSession(//NOPMD - suppressed ExcessiveParameterList
                                               Booking booking,
                                               RecordingOrigin origin,
                                               @Nullable String ingestAddress,
                                               @Nullable String liveOutputUrl,
                                               @Nullable Timestamp startedAt,
                                               @Nullable User startedBy,
                                               @Nullable Timestamp finishedAt,
                                               @Nullable User finishedBy,
                                               @Nullable RecordingStatus status,
                                               @Nullable Timestamp deletedAt) {
        CaptureSession captureSession = new CaptureSession();
        captureSession.setBooking(booking);
        captureSession.setOrigin(origin);
        captureSession.setIngestAddress(ingestAddress);
        captureSession.setLiveOutputUrl(liveOutputUrl);
        captureSession.setStartedAt(startedAt);
        captureSession.setStartedByUser(startedBy);
        captureSession.setFinishedAt(finishedAt);
        captureSession.setFinishedByUser(finishedBy);
        captureSession.setStatus(status);
        captureSession.setDeletedAt(deletedAt);
        return captureSession;
    }

    public Participant createParticipant(Case testCase,
                                         ParticipantType type,
                                         String firstName,
                                         String lastName,
                                         Timestamp deletedAt) {
        Participant participant = new Participant();
        participant.setCaseId(testCase);
        participant.setParticipantType(type);
        participant.setFirstName(firstName);
        participant.setLastName(lastName);
        participant.setDeletedAt(deletedAt);
        return participant;
    }

    public Region createRegion(String name, Set<Court> courts) {
        Region region = new Region();
        region.setName(name);
        region.setCourts(courts);
        return region;
    }

    public Room createRoom(String name, Set<Court> courts) {
        Room room = new Room();
        room.setName(name);
        room.setCourts(courts);
        return room;
    }

    public Recording createRecording(CaptureSession captureSession,
                                     @Nullable Recording parentRecording,
                                     int version,
                                     String url,
                                     String filename,
                                     @Nullable Timestamp deletedAt) {
        var recording = new Recording();
        recording.setCaptureSession(captureSession);
        recording.setParentRecording(parentRecording);
        recording.setVersion(version);
        recording.setUrl(url);
        recording.setFilename(filename);
        recording.setDeletedAt(deletedAt);
        return recording;
    }

    public ShareBooking createShareBooking(User sharedWith,
                                           User sharedBy,
                                           Booking booking,
                                           Timestamp deletedAt) {
        var share = new ShareBooking();
        share.setSharedWith(sharedWith);
        share.setSharedBy(sharedBy);
        share.setBooking(booking);
        share.setDeletedAt(deletedAt);
        return share;
    }

    public CreateParticipantDTO createParticipantDTO(String firstName,
                                                     String lastName,
                                                     ParticipantType participantType) {
        var participant = new CreateParticipantDTO();
        participant.setFirstName(firstName);
        participant.setLastName(lastName);
        participant.setParticipantType(participantType);
        return participant;
    }

    public CreateCourtDTO createCreateCourtDTO(CourtType courtType, String name, String locationCode) {
        var court = new CreateCourtDTO();
        court.setId(UUID.randomUUID());
        court.setCourtType(courtType);
        court.setName(name);
        court.setLocationCode(locationCode);
        return court;
    }

    public BaseUserDTO easyCreateBaseUserDTO() {
        var firstName = "Test" + ThreadLocalRandom.current().nextInt(0, 999999);
        return createBaseUserDTO(firstName,
                                 "User" + ThreadLocalRandom.current().nextInt(0, 999999),
                                 firstName + "@user.com",
                                 "12345678",
                                 "Test Organisation"
        );
    }

    public BaseUserDTO createBaseUserDTO(String firstName,
                                         String lastName,
                                         String email,
                                         String phoneNumber,
                                         String organisation) {
        var user = new BaseUserDTO();
        user.setId(UUID.randomUUID());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhoneNumber(phoneNumber);
        user.setOrganisation(organisation);
        return user;
    }

    public CaseDTO createCaseDTO(String reference) {
        var testCase = new CaseDTO();
        testCase.setId(UUID.randomUUID());
        testCase.setReference(reference);
        return testCase;
    }
}
