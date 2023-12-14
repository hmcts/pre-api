package uk.gov.hmcts.reform.preapi.util;

import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.Room;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.Set;
import javax.annotation.Nullable;

public class HelperFactory {

    private HelperFactory() {
    }

    public static User createDefaultTestUser() {
        return createUser(
            "Test",
            "User",
            "example@example.com",
            new Timestamp(System.currentTimeMillis()),
            null,
            null
        );
    }

    public static User createUser(
        String firstName,
        String lastName,
        String email,
        Timestamp deletedAt,
        @Nullable String phone,
        @Nullable String organisation
    ) { //NOPMD - suppressed UseObjectForClearerAPI
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setDeletedAt(deletedAt);
        user.setPhone(phone);
        user.setOrganisation(organisation);
        return user;
    }

    public static Court createCourt(CourtType courtType, String name, @Nullable String locationCode) {
        Court court = new Court();
        court.setCourtType(courtType);
        court.setName(name);
        court.setLocationCode(locationCode);
        return court;
    }

    public static Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    public static AppAccess createAppAccess(
        User user,
        Court court,
        Role role,
        boolean active,
        Timestamp deletedAt,
        @Nullable Date lastAccess
    ) {
        AppAccess appAccess = new AppAccess();
        appAccess.setUser(user);
        appAccess.setCourt(court);
        appAccess.setRole(role);
        appAccess.setLastAccess(lastAccess);
        appAccess.setActive(active);
        appAccess.setDeletedAt(deletedAt);
        return appAccess;
    }

    public static Case createCase(Court court, String reference, boolean test, Timestamp deletedAt) {
        Case testCase = new Case();
        testCase.setCourt(court);
        testCase.setReference(reference);
        testCase.setTest(test);
        testCase.setDeletedAt(deletedAt);
        return testCase;
    }

    public static Booking createBooking(Case testingCase,
                                        Timestamp scheduledFor,
                                        Timestamp deletedAt) {
        return createBooking(testingCase, scheduledFor, deletedAt, null);
    }

    public static Booking createBooking(Case testingCase,
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

    public static CaptureSession createCaptureSession(//NOPMD - suppressed ExcessiveParameterList
                                                      Booking booking,
                                                      RecordingOrigin origin,
                                                      @Nullable String ingestAddress,
                                                      @Nullable String liveOutputUrl,
                                                      @Nullable Timestamp startedAt,
                                                      @Nullable User startedBy,
                                                      @Nullable Timestamp finishedAt,
                                                      @Nullable User finishedBy,
                                                      @Nullable RecordingStatus status,
                                                      @Nullable Timestamp deletedAt
    ) {
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

    public static Participant createParticipant(
        Case testCase,
        ParticipantType type,
        String firstName,
        String lastName,
        Timestamp deletedAt
    ) {
        Participant participant = new Participant();
        participant.setCaseId(testCase);
        participant.setParticipantType(type);
        participant.setFirstName(firstName);
        participant.setLastName(lastName);
        participant.setDeletedAt(deletedAt);
        return participant;
    }

    public static Region createRegion(String name, Set<Court> courts) {
        Region region = new Region();
        region.setName(name);
        region.setCourts(courts);
        return region;
    }

    public static Room createRoom(String name, Set<Court> courts) {
        Room room = new Room();
        room.setName(name);
        room.setCourts(courts);
        return room;
    }
}
