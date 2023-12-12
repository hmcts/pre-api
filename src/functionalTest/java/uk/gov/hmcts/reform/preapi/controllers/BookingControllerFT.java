package uk.gov.hmcts.reform.preapi.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.Room;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.repositories.RegionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoomRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.sql.Timestamp;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingControllerFT extends FunctionalTestBase {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private CaptureSessionRepository captureSessionRepository;
    @Autowired
    private CaseRepository caseRepository;
    @Autowired
    private CourtRepository courtRepository;
    @Autowired
    private ParticipantRepository participantRepository;
    @Autowired
    private RecordingRepository recordingRepository;
    @Autowired
    private RegionRepository regionRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private UserRepository userRepository;


    private static final String BOOKINGS_ENDPOINT = "/cases/{0}/bookings/";
    private static final String RECORDINGS_ENDPOINT = "/bookings/{0}/recordings/";

    @Test
    void shouldRetrieveCourtDetail() {
        var court = new Court();
        court.setId(UUID.randomUUID());
        court.setName("Foo Court");
        court.setCourtType(CourtType.CROWN);
        court.setLocationCode("1234");
        courtRepository.save(court);

        var region = new Region();
        region.setName("Foo Region");
        region.setCourts(Set.of(court));
        court.setRegions(Set.of(region));
        regionRepository.save(region);

        var room = new Room();
        room.setName("Foo Room");
        room.setCourts(Set.of(court));
        roomRepository.save(room);

        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setReference("1234");
        caseEntity.setCourt(court);
        caseRepository.save(caseEntity);

        var participant1 = new Participant();
        participant1.setId(UUID.randomUUID());
        participant1.setParticipantType(ParticipantType.WITNESS);
        participant1.setCaseId(caseEntity);
        participant1.setFirstName("John");
        participant1.setLastName("Smith");
        var participant2 = new Participant();
        participant2.setId(UUID.randomUUID());
        participant2.setParticipantType(ParticipantType.DEFENDANT);
        participant2.setCaseId(caseEntity);
        participant2.setFirstName("Jane");
        participant2.setLastName("Doe");
        participantRepository.saveAll(Set.of(participant1, participant2));

        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(caseEntity);
        booking.setParticipants(Set.of(participant1, participant2));
        booking.setScheduledFor(Timestamp.valueOf("2024-06-28 12:00:00"));
        bookingRepository.save(booking);

        var finishUser = new User();
        finishUser.setId(UUID.randomUUID());
        finishUser.setEmail("finishuser@justice.local");
        finishUser.setPhone("0123456789");
        finishUser.setOrganisation("Gov Org");
        finishUser.setFirstName("Finish");
        finishUser.setLastName("User");
        var startUser = new User();
        startUser.setId(UUID.randomUUID());
        startUser.setEmail("startuser@justice.local");
        startUser.setPhone("0123456789");
        startUser.setOrganisation("Gov Org");
        startUser.setFirstName("Start");
        startUser.setLastName("User");
        userRepository.saveAll(Set.of(finishUser, startUser));

        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        captureSession.setOrigin(RecordingOrigin.PRE);
        captureSession.setStatus(RecordingStatus.AVAILABLE);
        captureSession.setStartedAt(Timestamp.valueOf("2024-06-28 12:00:00"));
        captureSession.setFinishedAt(Timestamp.valueOf("2024-06-28 12:30:00"));
        captureSession.setStartedByUser(startUser);
        captureSession.setFinishedByUser(finishUser);
        captureSession.setIngestAddress("http://localhost:8080/ingest");
        captureSession.setLiveOutputUrl("http://localhost:8080/live");
        captureSessionRepository.save(captureSession);

        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        recording.setVersion(1);
        recording.setUrl("http://localhost:8080/recording");
        recording.setFilename("recording.mp4");
        recording.setDuration(Duration.ofMinutes(30));
        recording.setEditInstruction("{\"foo\": \"bar\"}");

        var savedRec = recordingRepository.save(recording);
        assertThat(savedRec.getCreatedAt()).isNotNull();

        var bookingResponse = doGetRequest(
            MessageFormat.format(BOOKINGS_ENDPOINT, caseEntity.getId()) + booking.getId());
        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookingResponse.body().jsonPath().getString("id")).isEqualTo(booking.getId().toString());

        var recordingResponse = doGetRequest(
            MessageFormat.format(RECORDINGS_ENDPOINT, booking.getId()) + recording.getId());
        assertThat(recordingResponse.statusCode()).isEqualTo(200);
        assertThat(recordingResponse.body().jsonPath().getString("id")).isEqualTo(recording.getId().toString());

        var deleteResponse = doDeleteRequest(
            MessageFormat.format(BOOKINGS_ENDPOINT, caseEntity.getId()) + booking.getId());
        assertThat(deleteResponse.statusCode()).isEqualTo(204);

        var recordingResponse2 = doGetRequest(
            MessageFormat.format(RECORDINGS_ENDPOINT, booking.getId()) + recording.getId());
        assertThat(recordingResponse2.statusCode()).isEqualTo(404);
    }
}
