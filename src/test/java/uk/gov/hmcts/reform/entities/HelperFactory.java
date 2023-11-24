package uk.gov.hmcts.reform.entities;

import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
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
        boolean deleted,
        @Nullable String phone,
        @Nullable String organisation
    ) { //NOPMD - suppressed UseObjectForClearerAPI
        User user = new User();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setDeleted(deleted);
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
        boolean deleted,
        @Nullable Date lastAccess
    ) {
        AppAccess appAccess = new AppAccess();
        appAccess.setUser(user);
        appAccess.setCourt(court);
        appAccess.setRole(role);
        appAccess.setLastAccess(lastAccess);
        appAccess.setActive(active);
        appAccess.setDeleted(deleted);
        return appAccess;
    }

    static Case createCase(Court court, String caseRef, boolean test, boolean deleted) {
        Case testCase = new Case();
        testCase.setCourt(court);
        testCase.setCaseRef(caseRef);
        testCase.setTest(test);
        testCase.setDeleted(deleted);
        return testCase;
    }

    static Booking createBooking(Case testingCase, Date date, boolean deleted) {
        Booking booking = new Booking();
        booking.setCaseId(testingCase);
        booking.setDate(date);
        booking.setDeleted(deleted);
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
        boolean deleted
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
        captureSession.setDeleted(deleted);
        return captureSession;
    }

    static Participant createParticipant(
        Case testCase,
        ParticipantType type,
        String firstName,
        String lastName,
        boolean deleted
    ) {
        Participant participant = new Participant();
        participant.setCaseId(testCase);
        participant.setParticipantType(type);
        participant.setFirstName(firstName);
        participant.setLastName(lastName);
        participant.setDeleted(deleted);
        return participant;
    }
}
