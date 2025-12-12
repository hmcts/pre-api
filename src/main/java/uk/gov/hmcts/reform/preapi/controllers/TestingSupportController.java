package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfFailureReason;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.migration.VfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.repositories.RegionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserTermsAcceptedRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.lang.Character.toLowerCase;

@RestController
@RequestMapping("/testing-support")
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.ExcessiveImports", "PMD.TestClassWithoutTestCases"})
@ConditionalOnExpression("${testing-support-endpoints.enabled:false}")
class TestingSupportController {

    private final BookingRepository bookingRepository;
    private final CaptureSessionRepository captureSessionRepository;
    private final CaseRepository caseRepository;
    private final CourtRepository courtRepository;
    private final ParticipantRepository participantRepository;
    private final RecordingRepository recordingRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AppAccessRepository appAccessRepository;
    private final TermsAndConditionsRepository termsAndConditionsRepository;
    private final UserTermsAcceptedRepository userTermsAcceptedRepository;
    private final AuditRepository auditRepository;
    private final ScheduledTaskRunner scheduledTaskRunner;
    private final AzureFinalStorageService azureFinalStorageService;
    private final MigrationRecordRepository migrationRecordRepository;
    private final EditRequestService editRequestService;

    @Autowired
    @SuppressWarnings("PMD.ExcessiveParameterList")
    TestingSupportController(final BookingRepository bookingRepository,
                             final CaptureSessionRepository captureSessionRepository,
                             final CaseRepository caseRepository,
                             final CourtRepository courtRepository,
                             final ParticipantRepository participantRepository,
                             final RecordingRepository recordingRepository,
                             final RegionRepository regionRepository,
                             final UserRepository userRepository,
                             final RoleRepository roleRepository,
                             final AppAccessRepository appAccessRepository,
                             final TermsAndConditionsRepository termsAndConditionsRepository,
                             final UserTermsAcceptedRepository userTermsAcceptedRepository,
                             final ScheduledTaskRunner scheduledTaskRunner,
                             final AuditRepository auditRepository,
                             final AzureFinalStorageService azureFinalStorageService,
                             final MigrationRecordRepository migrationRecordRepository,
                             final EditRequestService editRequestService) {
        this.bookingRepository = bookingRepository;
        this.captureSessionRepository = captureSessionRepository;
        this.caseRepository = caseRepository;
        this.courtRepository = courtRepository;
        this.participantRepository = participantRepository;
        this.recordingRepository = recordingRepository;
        this.regionRepository = regionRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.appAccessRepository = appAccessRepository;
        this.termsAndConditionsRepository = termsAndConditionsRepository;
        this.userTermsAcceptedRepository = userTermsAcceptedRepository;
        this.auditRepository = auditRepository;
        this.scheduledTaskRunner = scheduledTaskRunner;
        this.azureFinalStorageService = azureFinalStorageService;
        this.migrationRecordRepository = migrationRecordRepository;
        this.editRequestService = editRequestService;
    }

    @PostMapping(path = "/create-region", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createRegion(@RequestParam(required = false) String regionName) {
        Region region = new Region();
        region.setName(regionName == null || regionName.isEmpty()  ? "Example Region" : regionName);
        regionRepository.save(region);

        return ResponseEntity.ok(Map.of("regionId", region.getId().toString(), "regionName", region.getName()));
    }

    @PostMapping(path = "/create-court", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createCourt() {
        Court court = createTestCourt();

        Map<String, String> response = Map.of("courtId", court.getId().toString());
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/create-well-formed-booking", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> shouldNotHavePastScheduledForDate() {
        Court court = createTestCourt();

        Region region = new Region();
        region.setName("Foo Region");
        region.setCourts(Set.of(court));
        court.setRegions(Set.of(region));
        regionRepository.save(region);

        court.setRegions(Set.of(region));
        courtRepository.save(court);

        Case caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setReference("4567890123");
        caseEntity.setCourt(court);
        caseEntity.setOrigin(RecordingOrigin.PRE);
        caseRepository.save(caseEntity);

        Participant participant1 = new Participant();
        participant1.setId(UUID.randomUUID());
        participant1.setParticipantType(ParticipantType.WITNESS);
        participant1.setCaseId(caseEntity);
        participant1.setFirstName("John");
        participant1.setLastName("Smith");
        Participant participant2 = new Participant();
        participant2.setId(UUID.randomUUID());
        participant2.setParticipantType(ParticipantType.DEFENDANT);
        participant2.setCaseId(caseEntity);
        participant2.setFirstName("Jane");
        participant2.setLastName("Doe");
        participantRepository.saveAll(Set.of(participant1, participant2));

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(caseEntity);
        booking.setParticipants(Set.of(participant1, participant2));
        booking.setScheduledFor(Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant()));
        bookingRepository.save(booking);

        Map<String, String> response = Map.of(
            "bookingId", booking.getId().toString(),
            "courtId", court.getId().toString(),
            "caseId", caseEntity.getId().toString()
        );
        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("PMD.NcssCount")
    @PostMapping(path = "/should-delete-recordings-for-booking", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> shouldDeleteRecordingsForBooking() {
        Court court = createTestCourt();

        Region region = new Region();
        region.setName("Region " + RandomStringUtils.secure().nextAlphabetic(5));
        region.setCourts(Set.of(court));
        court.setRegions(Set.of(region));
        regionRepository.save(region);

        Case caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setReference(RandomStringUtils.secure().nextAlphabetic(9));
        caseEntity.setCourt(court);
        caseEntity.setOrigin(RecordingOrigin.PRE);
        caseRepository.save(caseEntity);

        Participant participant1 = new Participant();
        participant1.setId(UUID.randomUUID());
        participant1.setParticipantType(ParticipantType.WITNESS);
        participant1.setCaseId(caseEntity);
        participant1.setFirstName("John");
        participant1.setLastName("Smith");
        Participant participant2 = new Participant();
        participant2.setId(UUID.randomUUID());
        participant2.setParticipantType(ParticipantType.DEFENDANT);
        participant2.setCaseId(caseEntity);
        participant2.setFirstName("Jane");
        participant2.setLastName("Doe");
        participantRepository.saveAll(Set.of(participant1, participant2));

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(caseEntity);
        booking.setParticipants(Set.of(participant1, participant2));
        OffsetDateTime scheduledFor = OffsetDateTime.now().plusWeeks(1);
        booking.setScheduledFor(Timestamp.from(scheduledFor.toInstant()));
        bookingRepository.save(booking);

        User finishUser = new User();
        finishUser.setId(UUID.randomUUID());
        finishUser.setEmail("finishuser@justice.local");
        finishUser.setPhone(RandomStringUtils.secure().nextNumeric(11));
        finishUser.setOrganisation("Gov Org");
        finishUser.setFirstName("Finish");
        finishUser.setLastName("User");
        User startUser = new User();
        startUser.setId(UUID.randomUUID());
        startUser.setEmail("startuser@justice.local");
        startUser.setPhone(RandomStringUtils.secure().nextNumeric(11));
        startUser.setOrganisation("Gov Org");
        startUser.setFirstName("Start");
        startUser.setLastName("User");
        userRepository.saveAll(Set.of(finishUser, startUser));

        CaptureSession captureSession = new CaptureSession();
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

        Recording recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        recording.setVersion(1);
        recording.setFilename("recording.mp4");
        recording.setDuration(Duration.ofMinutes(3));
        recording.setEditInstruction("{\"foo\": \"bar\"}");

        recordingRepository.save(recording);

        Map<String, String> response = Map.of(
            "caseId", caseEntity.getId().toString(),
            "bookingId", booking.getId().toString(),
            "recordingId", recording.getId().toString(),
            "captureSessionId", captureSession.getId().toString()
        );
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
        String roleStr = switch (roleName) {
            case SUPER_USER -> "Super User";
            case LEVEL_1 -> "Level 1";
            case LEVEL_2 -> "Level 2";
            case LEVEL_3 -> "Level 3";
            case LEVEL_4 -> "Level 4";
        };

        Role role = createRole(roleStr);
        Map<String, String> response = Map.of("roleId", role.getId().toString());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-authenticated-user/{role}")
    public ResponseEntity<Map<String, String>> createAuthenticatedUser(@PathVariable TestingSupportRoles role) {
        String roleName = switch (role) {
            case SUPER_USER ->  "Super User";
            case LEVEL_1 -> "Level 1";
            case LEVEL_2 -> "Level 2";
            case LEVEL_3 -> "Level 3";
            case LEVEL_4 -> "Level 4";
        };
        Role roleEntity = roleRepository.findFirstByName(roleName)
            .orElse(createRole(roleName));
        AppAccess appAccess = createAppAccess(roleEntity);
        return ResponseEntity.ok(Map.of(
            "accessId", appAccess.getId().toString(),
            "courtId", appAccess.getCourt().getId().toString()
        ));
    }

    @PostMapping(value = "/create-ready-to-use-booking/{caseReference}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createReadyToUseBooking(@PathVariable String caseReference) {
        List<Case> cases = caseRepository.findAllByReference(caseReference);
        if (cases.isEmpty()) {
            throw new NotFoundException("Only use this endpoint for cases that already exist");
        }
        Case aCase = cases.getFirst();

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);
        booking.setParticipants(aCase.getParticipants());
        booking.setScheduledFor(Timestamp.from(Instant.now()));
        bookingRepository.save(booking);

        CaptureSession captureSession = new CaptureSession();
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
        TermsAndConditions terms = new TermsAndConditions();
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

    @Parameter(
        name = "page",
        description = "The page number of search result to return",
        schema = @Schema(implementation = Integer.class),
        example = "0"
    )
    @Parameter(
        name = "size",
        description = "The number of search results to return per page",
        schema = @Schema(implementation = Integer.class),
        example = "10"
    )
    @GetMapping("/latest-audits")
    public HttpEntity<PagedModel<EntityModel<Audit>>> getLatestAudits(
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<Audit> assembler
    ) {
        Page<Audit> resultPage = auditRepository.findLatest(pageable);

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));
    }

    @PostMapping(value = "/booking-scheduled-for-past/{bookingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingDTO> bookingScheduledForPast(@PathVariable UUID bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(bookingId)
            .orElseThrow(() -> new NotFoundException("Booking: " + bookingId));

        booking.setScheduledFor(Timestamp.from(booking.getScheduledFor().toInstant().minusSeconds(31536000)));
        bookingRepository.save(booking);

        return ResponseEntity.ok(new BookingDTO(booking));
    }

    @SneakyThrows
    @PostMapping(value = "/trigger-edit-request-processing/{editId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> triggerEditRequestProcessing(@PathVariable UUID editId) {
        var r = roleRepository.findFirstByName("Super User")
            .orElse(createRole("Super User"));
        var appAccess = createAppAccess(r);
        SecurityContextHolder.getContext()
            .setAuthentication(new UserAuthentication(
                appAccess,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_USER"))));

        EditRequest editRequest = new EditRequest();
        editRequest.setId(editId);
        editRequest.setStatus(EditRequestStatus.PROCESSING);
        var recording = editRequestService.performEdit(editRequest);
        var request = editRequestService.findById(editId);

        return ResponseEntity.ok(Map.of(
            "request", request,
            "recording", recording
        ));
    }

    @PostMapping(value = "/trigger-task/{taskName}")
    public ResponseEntity<Void> triggerTask(@PathVariable String taskName) {
        final String beanName = toLowerCase(taskName.charAt(0)) + taskName.substring(1);
        Runnable task = scheduledTaskRunner.getTask(beanName);
        if (task == null) {
            throw new NotFoundException("Task: " + taskName);
        }
        task.run();

        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @PostMapping(value = "/create-existing-v1-recording/{recordingId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecordingDTO> createExistingRecording(@PathVariable UUID recordingId) {
        // done first so that we can get 404 if recording doesn't actually exist
        String mp4FileName = azureFinalStorageService.getMp4FileName(recordingId.toString());

        Court court = createTestCourt();

        Region region = new Region();
        region.setName("Foo Region");
        region.setCourts(Set.of(court));
        court.setRegions(Set.of(region));
        regionRepository.save(region);

        court.setRegions(Set.of(region));
        courtRepository.save(court);

        Case caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setReference("4567890123");
        caseEntity.setCourt(court);
        caseEntity.setOrigin(RecordingOrigin.PRE);
        caseRepository.save(caseEntity);

        Participant participant1 = new Participant();
        participant1.setId(UUID.randomUUID());
        participant1.setParticipantType(ParticipantType.WITNESS);
        participant1.setCaseId(caseEntity);
        participant1.setFirstName("John");
        participant1.setLastName("Smith");
        Participant participant2 = new Participant();
        participant2.setId(UUID.randomUUID());
        participant2.setParticipantType(ParticipantType.DEFENDANT);
        participant2.setCaseId(caseEntity);
        participant2.setFirstName("Jane");
        participant2.setLastName("Doe");
        participantRepository.saveAll(Set.of(participant1, participant2));

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(caseEntity);
        booking.setParticipants(Set.of(participant1, participant2));
        booking.setScheduledFor(Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant()));
        bookingRepository.save(booking);

        CaptureSession captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        captureSession.setOrigin(RecordingOrigin.PRE);
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSession.setStartedAt(Timestamp.from(Instant.now()));
        captureSession.setFinishedAt(Timestamp.from(Instant.now()));
        captureSessionRepository.save(captureSession);

        Recording recording = new Recording();
        recording.setId(recordingId);
        recording.setVersion(1);
        recording.setCaptureSession(captureSession);
        recording.setFilename(mp4FileName);
        recording.setCreatedAt(Timestamp.from(Instant.now()));
        recordingRepository.save(recording);

        return ResponseEntity.ok(new RecordingDTO(recording));
    }

    @PostMapping("/create-random-formed-migration-record")
    public ResponseEntity<VfMigrationRecordDTO> createRandomFormedMigrationRecord() {
        String randomArchiveId = UUID.randomUUID().toString();
        String randomArchiveName = "archive_" + randomArchiveId.substring(0, 8);
        MigrationRecord record = new MigrationRecord();
        record.setId(UUID.randomUUID());
        record.setArchiveId(randomArchiveId);
        record.setArchiveName(randomArchiveName);
        record.setCreateTime(new Timestamp(System.currentTimeMillis()));
        record.setDuration((int) (Math.random() * 1000));
        record.setCourtReference(courtRepository.findAll().getFirst().getName());
        record.setUrn("urn" + (int) (Math.random() * 100000));
        record.setExhibitReference(RandomStringUtils.randomAlphabetic(10));
        record.setDefendantName("defendant");
        record.setWitnessName("witness");
        record.setRecordingVersion("ORIG");
        record.setRecordingVersionNumber("1");
        record.setFileName("file_" + randomArchiveId.substring(0, 4) + ".mp4");
        record.setFileSizeMb(String.valueOf((Math.random() * 100) + 1));
        record.setRecordingId(UUID.randomUUID());
        record.setCaptureSessionId(UUID.randomUUID());
        record.setBookingId(UUID.randomUUID());
        record.setStatus(VfMigrationStatus.FAILED);
        record.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        record.setReason(VfFailureReason.GENERAL_ERROR.toString());
        record.setErrorMessage("error_message");
        record.setIsMostRecent(true);
        record.setIsPreferred(true);
        record.setRecordingGroupKey("group_key");
        migrationRecordRepository.saveAndFlush(record);
        return ResponseEntity.ok(new VfMigrationRecordDTO(record));
    }

    @PostMapping("/create-random-empty-migration-record")
    public ResponseEntity<VfMigrationRecordDTO> createRandomEmptyMigrationRecord() {
        String randomArchiveId = UUID.randomUUID().toString();
        String randomArchiveName = "archive_" + randomArchiveId.substring(0, 8);
        MigrationRecord record = new MigrationRecord();
        record.setId(UUID.randomUUID());
        record.setArchiveId(randomArchiveId);
        record.setArchiveName(randomArchiveName);
        record.setCreateTime(new Timestamp(System.currentTimeMillis()));
        record.setDuration((int) (Math.random() * 1000));
        record.setRecordingVersionNumber("1");
        record.setFileName("file_" + randomArchiveId.substring(0, 4) + ".mp4");
        record.setFileSizeMb(String.valueOf((Math.random() * 100) + 1));
        record.setStatus(VfMigrationStatus.FAILED);
        record.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        record.setReason(VfFailureReason.INCOMPLETE_DATA.toString());
        record.setErrorMessage("error_message");
        record.setIsMostRecent(true);
        record.setIsPreferred(true);
        record.setRecordingGroupKey("group_key");
        migrationRecordRepository.saveAndFlush(record);
        return ResponseEntity.ok(new VfMigrationRecordDTO(record));
    }

    @PostMapping("/update-migration-record-to-invalid-duration/{migrationRecordId}")
    public ResponseEntity<VfMigrationRecordDTO> updateMigrationRecordToInvalidDuration(
        @PathVariable UUID migrationRecordId) {

        MigrationRecord record = migrationRecordRepository.findById(migrationRecordId)
            .orElseThrow(() -> new NotFoundException("MigrationRecord: " + migrationRecordId));
        record.setDuration(5); // must be more than 10 seconds, so this is invalid
        record.setReason(VfFailureReason.INVALID_FORMAT.toString());
        record.setErrorMessage("Duration too short");
        migrationRecordRepository.saveAndFlush(record);

        return ResponseEntity.ok(new VfMigrationRecordDTO(record));
    }

    private Court createTestCourt() {
        Court court = new Court();
        court.setId(UUID.randomUUID());
        court.setName("Foo Court");
        court.setCourtType(CourtType.CROWN);
        court.setLocationCode(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        courtRepository.save(court);

        return court;
    }

    private AppAccess createAppAccess(Role role) {
        AppAccess access = new AppAccess();
        access.setUser(createUser());
        access.setCourt(createTestCourt());
        access.setRole(role);
        access.setActive(true);
        access.setDefaultCourt(true);
        appAccessRepository.save(access);

        return access;
    }

    private User createUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(user.getId() + "@example.com");
        user.setFirstName("Example");
        user.setLastName("Example");
        user.setPhone("0987654321");
        user.setOrganisation("ExampleOrg");
        userRepository.save(user);

        return user;
    }

    private Role createRole(String roleName) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(roleName);
        roleRepository.save(role);

        return role;
    }
}
