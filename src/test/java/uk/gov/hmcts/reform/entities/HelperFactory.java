package uk.gov.hmcts.reform.entities;

import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Date;
import java.sql.Timestamp;
import javax.annotation.Nullable;

final class HelperFactory {

    private HelperFactory() {
    }

    static User createUser(
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

    static Court createCourt(CourtType courtType, String name, @Nullable String locationCode) {
        Court court = new Court();
        court.setCourtType(courtType);
        court.setName(name);
        court.setLocationCode(locationCode);
        return court;
    }

    static Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    static AppAccess createAppAccess(
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

    static Case createCase(Court court, String reference, boolean test, Timestamp deletedAt) {
        Case testCase = new Case();
        testCase.setCourt(court);
        testCase.setReference(reference);
        testCase.setTest(test);
        testCase.setDeletedAt(deletedAt);
        return testCase;
    }

    static Booking createBooking(Case testingCase, Timestamp scheduledFor, Timestamp deletedAt) {
        Booking booking = new Booking();
        booking.setCaseId(testingCase);
        booking.setScheduledFor(scheduledFor);
        booking.setDeletedAt(deletedAt);
        return booking;
    }

    static CaptureSession createCaptureSession(//NOPMD - suppressed ExcessiveParameterList
        Booking booking,
        RecordingOrigin origin,
        @Nullable String ingestAddress,
        @Nullable String liveOutputUrl,
        @Nullable Timestamp startedOn,
        @Nullable User startedBy,
        @Nullable Timestamp finishedOn,
        @Nullable User finishedBy,
        @Nullable RecordingStatus status,
        @Nullable Timestamp deletedAt
    ) {
        CaptureSession captureSession = new CaptureSession();
        captureSession.setBooking(booking);
        captureSession.setOrigin(origin);
        captureSession.setIngestAddress(ingestAddress);
        captureSession.setLiveOutputUrl(liveOutputUrl);
        captureSession.setStartedOn(startedOn);
        captureSession.setStartedByUser(startedBy);
        captureSession.setFinishedOn(finishedOn);
        captureSession.setFinishedByUserId(finishedBy);
        captureSession.setStatus(status);
        captureSession.setDeletedAt(deletedAt);
        return captureSession;
    }

    static Participant createParticipant(
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
}
