package uk.gov.hmcts.reform.preapi.controllers;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/testing-support")
@ConditionalOnExpression("${testing-support-endpoints.enabled:false}")
class TestingSupportController {

    private final BookingRepository bookingRepository;
    private final CaptureSessionRepository captureSessionRepository;
    private final CaseRepository caseRepository;
    private final CourtRepository courtRepository;
    private final ParticipantRepository participantRepository;
    private final RecordingRepository recordingRepository;
    private final RegionRepository regionRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Autowired
    TestingSupportController(final BookingRepository bookingRepository,
                             final CaptureSessionRepository captureSessionRepository,
                             final CaseRepository caseRepository,
                             final CourtRepository courtRepository,
                             final ParticipantRepository participantRepository,
                             final RecordingRepository recordingRepository,
                             final RegionRepository regionRepository,
                             final RoomRepository roomRepository,
                             final UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.captureSessionRepository = captureSessionRepository;
        this.caseRepository = caseRepository;
        this.courtRepository = courtRepository;
        this.participantRepository = participantRepository;
        this.recordingRepository = recordingRepository;
        this.regionRepository = regionRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @PostMapping(path = "/should-delete-recordings-for-booking", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> shouldDeleteRecordingsForBooking() {
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

        recordingRepository.save(recording);

        var response = new HashMap<String, String>() {
            {
                put("caseId", caseEntity.getId().toString());
                put("bookingId", booking.getId().toString());
                put("recordingId", recording.getId().toString());
            }
        };

        return ResponseEntity.ok(response);
    }
}
