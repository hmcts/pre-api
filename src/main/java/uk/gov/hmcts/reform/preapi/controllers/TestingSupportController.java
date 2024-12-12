package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.Room;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.repositories.RegionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoomRepository;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserTermsAcceptedRepository;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.lang.Character.toLowerCase;

@RestController
@RequestMapping("/testing-support")
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
    private final RoleRepository roleRepository;
    private final AppAccessRepository appAccessRepository;
    private final TermsAndConditionsRepository termsAndConditionsRepository;
    private final UserTermsAcceptedRepository userTermsAcceptedRepository;
    private final ScheduledTaskRunner scheduledTaskRunner;

    @Autowired
    TestingSupportController(final BookingRepository bookingRepository,
                             final CaptureSessionRepository captureSessionRepository,
                             final CaseRepository caseRepository,
                             final CourtRepository courtRepository,
                             final ParticipantRepository participantRepository,
                             final RecordingRepository recordingRepository,
                             final RegionRepository regionRepository,
                             final RoomRepository roomRepository,
                             final UserRepository userRepository,
                             final RoleRepository roleRepository,
                             final AppAccessRepository appAccessRepository,
                             final TermsAndConditionsRepository termsAndConditionsRepository,
                             final UserTermsAcceptedRepository userTermsAcceptedRepository,
                             final ScheduledTaskRunner scheduledTaskRunner) {
        this.bookingRepository = bookingRepository;
        this.captureSessionRepository = captureSessionRepository;
        this.caseRepository = caseRepository;
        this.courtRepository = courtRepository;
        this.participantRepository = participantRepository;
        this.recordingRepository = recordingRepository;
        this.regionRepository = regionRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.appAccessRepository = appAccessRepository;
        this.termsAndConditionsRepository = termsAndConditionsRepository;
        this.userTermsAcceptedRepository = userTermsAcceptedRepository;
        this.scheduledTaskRunner = scheduledTaskRunner;
    }

    @PostMapping(path = "/create-room", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createRoom(@RequestParam(required = false) String roomName) {
        var room = new Room();
        room.setName(roomName == null || roomName.isEmpty()  ? "Example Room" : roomName);
        roomRepository.save(room);

        return ResponseEntity.ok(Map.of("roomId", room.getId().toString(), "roomName", room.getName()));
    }

    @PostMapping(path = "/create-region", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createRegion(@RequestParam(required = false) String regionName) {
        var region = new Region();
        region.setName(regionName == null || regionName.isEmpty()  ? "Example Region" : regionName);
        regionRepository.save(region);

        return ResponseEntity.ok(Map.of("regionId", region.getId().toString(), "regionName", region.getName()));
    }

    @PostMapping(path = "/create-court", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createCourt() {
        var court = createTestCourt();

        var response = new HashMap<String, String>() {
            {
                put("courtId", court.getId().toString());
            }
        };

        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/create-well-formed-booking", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> shouldNotHavePastScheduledForDate() {
        var court = createTestCourt();

        var region = new Region();
        region.setName("Foo Region");
        region.setCourts(Set.of(court));
        court.setRegions(Set.of(region));
        regionRepository.save(region);

        var room = new Room();
        room.setName("Foo Room");
        room.setCourts(Set.of(court));
        roomRepository.save(room);

        court.setRegions(Set.of(region));
        court.setRooms(Set.of(room));
        courtRepository.save(court);

        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setReference("4567890123");
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
        booking.setScheduledFor(Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant()));
        bookingRepository.save(booking);

        var response = new HashMap<String, String>() {
            {
                put("bookingId", booking.getId().toString());
                put("courtId", court.getId().toString());
                put("caseId", caseEntity.getId().toString());
            }
        };

        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/should-delete-recordings-for-booking", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> shouldDeleteRecordingsForBooking() {
        var court = createTestCourt();

        var region = new Region();
        region.setName("Region " + RandomStringUtils.randomAlphabetic(5));
        region.setCourts(Set.of(court));
        court.setRegions(Set.of(region));
        regionRepository.save(region);

        var room = new Room();
        room.setName("Room " + RandomStringUtils.randomAlphabetic(5));
        room.setCourts(Set.of(court));
        roomRepository.save(room);

        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setReference(RandomStringUtils.randomAlphabetic(5));
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
        var scheduledFor = OffsetDateTime.now().plusWeeks(1);
        booking.setScheduledFor(Timestamp.from(scheduledFor.toInstant()));
        bookingRepository.save(booking);

        var finishUser = new User();
        finishUser.setId(UUID.randomUUID());
        finishUser.setEmail("finishuser@justice.local");
        finishUser.setPhone(RandomStringUtils.randomNumeric(11));
        finishUser.setOrganisation("Gov Org");
        finishUser.setFirstName("Finish");
        finishUser.setLastName("User");
        var startUser = new User();
        startUser.setId(UUID.randomUUID());
        startUser.setEmail("startuser@justice.local");
        startUser.setPhone(RandomStringUtils.randomNumeric(11));
        startUser.setOrganisation("Gov Org");
        startUser.setFirstName("Start");
        startUser.setLastName("User");
        userRepository.saveAll(Set.of(finishUser, startUser));

        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        captureSession.setOrigin(RecordingOrigin.PRE);
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSession.setStartedAt(booking.getScheduledFor());
        captureSession.setFinishedAt(Timestamp.from(scheduledFor.plusMinutes(30).toInstant()));
        captureSession.setStartedByUser(startUser);
        captureSession.setFinishedByUser(finishUser);
        captureSession.setIngestAddress("http://localhost:8080/ingest");
        captureSession.setLiveOutputUrl("http://localhost:8080/live");
        captureSessionRepository.save(captureSession);

        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        recording.setVersion(1);
        recording.setFilename("recording.mp4");
        recording.setDuration(Duration.ofMinutes(30));
        recording.setEditInstruction("{\"foo\": \"bar\"}");

        recordingRepository.save(recording);

        var response = new HashMap<String, String>() {
            {
                put("caseId", caseEntity.getId().toString());
                put("bookingId", booking.getId().toString());
                put("recordingId", recording.getId().toString());
                put("captureSessionId", captureSession.getId().toString());
            }
        };

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/create-role")
    public ResponseEntity<Map<String, String>> createSuperUserRole(
        @Parameter(
            name = "roleName",
            schema = @Schema(implementation = TestingSupportRoles.class),
            required = true
        ) TestingSupportRoles roleName
    ) {
        String roleStr;
        switch (roleName) {
            case SUPER_USER -> roleStr = "Super User";
            case LEVEL_1 -> roleStr = "Level 1";
            case LEVEL_2 -> roleStr = "Level 2";
            case LEVEL_3 -> roleStr = "Level 3";
            case LEVEL_4 -> roleStr = "Level 4";
            default -> roleStr = "Other Role";
        }

        var role = createRole(roleStr);
        var response = Map.of("roleId", role.getId().toString());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-authenticated-user/{role}")
    public ResponseEntity<Map<String, String>> createAuthenticatedUser(@PathVariable TestingSupportRoles role) {
        String roleName;
        switch (role) {
            case SUPER_USER -> roleName = "Super User";
            case LEVEL_1 -> roleName = "Level 1";
            case LEVEL_2 -> roleName = "Level 2";
            case LEVEL_3 -> roleName = "Level 3";
            case LEVEL_4 -> roleName = "Level 4";
            default -> throw new IllegalArgumentException("Invalid role");
        }
        var r = roleRepository.findFirstByName(roleName)
            .orElse(createRole(roleName));
        var appAccess = createAppAccess(r);
        return ResponseEntity.ok(Map.of(
            "accessId", appAccess.getId().toString(),
            "courtId", appAccess.getCourt().getId().toString()
        ));
    }

    @PostMapping(value = "/create-ready-to-use-booking/{caseReference}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createReadyToUseBooking(@PathVariable String caseReference) {
        var cases = caseRepository.findAllByReference(caseReference);
        if (cases.isEmpty()) {
            throw new NotFoundException("Only use this endpoint for cases that already exist");
        }
        var aCase = cases.getFirst();

        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);
        booking.setParticipants(aCase.getParticipants());
        booking.setScheduledFor(Timestamp.from(Instant.now()));
        bookingRepository.save(booking);

        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        captureSession.setOrigin(RecordingOrigin.PRE);
        captureSession.setStatus(RecordingStatus.INITIALISING);
        captureSessionRepository.save(captureSession);

        return ResponseEntity.ok(Map.of(
            "caseId", aCase.getId().toString(),
            "bookingId", booking.getId().toString(),
            "captureSessionId", captureSession.getId().toString())
        );
    }

    @PostMapping(value = "/create-terms-and-conditions/{termsType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createTermsAndConditions(
        @PathVariable TermsAndConditionsType termsType
    ) {
        var terms = new TermsAndConditions();
        terms.setId(UUID.randomUUID());
        terms.setType(termsType);
        terms.setContent("some terms and conditions content");
        terms.setCreatedAt(Timestamp.from(Instant.now()));
        termsAndConditionsRepository.save(terms);

        return ResponseEntity.ok(Map.of("termsId", terms.getId().toString()));
    }

    @PostMapping(value = "/outdate-all-user-acceptances", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> outdateAllUserAcceptances() {
        userTermsAcceptedRepository.findAll()
            .forEach(a -> {
                a.setAcceptedAt(Timestamp.from(a.getAcceptedAt().toInstant().minusSeconds(31536000)));
                userTermsAcceptedRepository.save(a);
            });

        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/booking-scheduled-for-past/{bookingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingDTO> bookingScheduledForPast(@PathVariable UUID bookingId) {
        var booking = bookingRepository.findByIdAndDeletedAtIsNull(bookingId)
            .orElseThrow(() -> new NotFoundException("Booking: " + bookingId));

        booking.setScheduledFor(Timestamp.from(booking.getScheduledFor().toInstant().minusSeconds(31536000)));
        bookingRepository.save(booking);

        return ResponseEntity.ok(new BookingDTO(booking));
    }

    @PostMapping(value = "/trigger-task/{taskName}")
    public ResponseEntity<Void> triggerTask(@PathVariable String taskName) {
        final var beanName = toLowerCase(taskName.charAt(0)) + taskName.substring(1);
        var task = scheduledTaskRunner.getTask(beanName);
        if (task == null) {
            throw new NotFoundException("Task: " + taskName);
        }
        task.run();

        return ResponseEntity.noContent().build();
    }

    private Court createTestCourt() {
        var court = new Court();
        court.setId(UUID.randomUUID());
        court.setName("Foo Court");
        court.setCourtType(CourtType.CROWN);
        court.setLocationCode(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        courtRepository.save(court);

        return court;
    }

    private AppAccess createAppAccess(String role) {
        var access = new AppAccess();
        access.setUser(createUser());
        access.setCourt(createTestCourt());
        access.setRole(createRole(role));
        access.setActive(true);
        access.setDefaultCourt(true);
        appAccessRepository.save(access);

        return access;
    }

    private AppAccess createAppAccess(Role role) {
        var access = new AppAccess();
        access.setUser(createUser());
        access.setCourt(createTestCourt());
        access.setRole(role);
        access.setActive(true);
        access.setDefaultCourt(true);
        appAccessRepository.save(access);

        return access;
    }

    private User createUser() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(user.getId() + "@example.com");
        user.setFirstName("Example");
        user.setLastName("Example");
        user.setPhone("0987654321");
        user.setOrganisation("ExampleOrg");
        userRepository.save(user);

        return user;
    }

    private Role createRole(String r) {
        var role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(r);
        roleRepository.save(role);

        return role;
    }

    public enum AuthLevel {
        NONE,
        SUPER_USER,
        LEVEL_1,
        LEVEL_2,
        LEVEL_3,
        LEVEL_4
    }
}
