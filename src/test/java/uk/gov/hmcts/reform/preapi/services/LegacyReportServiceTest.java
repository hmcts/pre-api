package uk.gov.hmcts.reform.preapi.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = LegacyReportService.class)
public class LegacyReportServiceTest {
    private static Recording recordingEntity;
    private static CaptureSession captureSessionEntity;
    private static Court courtEntity;
    private static Region regionEntity;
    private static Case caseEntity;
    private static Booking bookingEntity;
    private static Audit auditEntity;

    @MockitoBean
    private CaptureSessionRepository captureSessionRepository;

    @MockitoBean
    private RecordingRepository recordingRepository;

    @MockitoBean
    private ShareBookingRepository shareBookingRepository;

    @MockitoBean
    private AuditRepository auditRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AppAccessRepository appAccessRepository;

    @MockitoBean
    private PortalAccessRepository portalAccessRepository;

    @Autowired
    private LegacyReportService reportService;

    @BeforeAll
    static void setUp() {
        regionEntity = new Region();
        regionEntity.setId(UUID.randomUUID());
        regionEntity.setName("London");

        courtEntity = new Court();
        courtEntity.setId(UUID.randomUUID());
        courtEntity.setName("Example Court");
        courtEntity.setRegions(Set.of(regionEntity));

        recordingEntity = new Recording();
        recordingEntity.setId(UUID.randomUUID());

        caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setCourt(courtEntity);
        caseEntity.setReference("ABC123");

        bookingEntity = new Booking();
        bookingEntity.setId(UUID.randomUUID());
        bookingEntity.setCaseId(caseEntity);

        captureSessionEntity = new CaptureSession();
        captureSessionEntity.setId(UUID.randomUUID());
        captureSessionEntity.setBooking(bookingEntity);

        recordingEntity.setCaptureSession(captureSessionEntity);
        recordingEntity.setVersion(1);
        recordingEntity.setFilename("example-filename.txt");
        recordingEntity.setCreatedAt(Timestamp.from(Instant.now()));

        auditEntity = new Audit();
        auditEntity.setId(UUID.randomUUID());
        auditEntity.setCreatedAt(Timestamp.from(Instant.now()));
        auditEntity.setTableRecordId(recordingEntity.getId());
    }


    @BeforeEach
    void reset() {
        captureSessionEntity.setStartedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setFinishedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setStatus(null);
        recordingEntity.setDuration(null);
        recordingEntity.setVersion(1);
        recordingEntity.setParentRecording(null);
        auditEntity.setSource(null);
        auditEntity.setCreatedBy(null);
        auditEntity.setAuditDetails(null);
        bookingEntity.setParticipants(Set.of());
    }

    @DisplayName("Find all capture sessions and return a list of models as a report when capture session is incomplete")
    @Test
    void captureSessionReportCaptureSessionIncompleteSuccess() {
        captureSessionEntity.setStartedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setFinishedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setRecordings(Set.of());
        when(captureSessionRepository.reportConcurrentCaptureSessions()).thenReturn(List.of(captureSessionEntity));

        var report = reportService.reportCaptureSessions();

        verify(captureSessionRepository, times(1)).reportConcurrentCaptureSessions();

        assertThat(report.size()).isEqualTo(1);
        var first = report.getFirst();

        assertThat(first.getId()).isEqualTo(captureSessionEntity.getId());
        assertThat(first.getStartTime()).isEqualTo(captureSessionEntity.getStartedAt());
        assertThat(first.getEndTime()).isEqualTo(captureSessionEntity.getFinishedAt());
        assertThat(first.getDuration()).isNull();
        assertThat(first.getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(first.getCourt()).isEqualTo(courtEntity.getName());
        assertThat(first.getRegion().stream().findFirst().isPresent()).isTrue();
        assertThat(first.getRegion().stream().findFirst().get().getName()).isEqualTo(regionEntity.getName());
    }

    @DisplayName("Find all capture sessions and return a list of models as a report on concurrent capture sessions")
    @Test
    void captureSessionReportConcurrentCaptureSessionsSuccess() {
        recordingEntity.setDuration(Duration.ofMinutes(3));
        captureSessionEntity.setStartedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setFinishedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setRecordings(Set.of(recordingEntity));
        when(captureSessionRepository.reportConcurrentCaptureSessions()).thenReturn(List.of(captureSessionEntity));

        var report = reportService.reportCaptureSessions();

        verify(captureSessionRepository, times(1)).reportConcurrentCaptureSessions();

        assertThat(report.size()).isEqualTo(1);
        var first = report.getFirst();

        assertThat(first.getId()).isEqualTo(captureSessionEntity.getId());
        assertThat(first.getStartTime()).isEqualTo(captureSessionEntity.getStartedAt());
        assertThat(first.getEndTime()).isEqualTo(captureSessionEntity.getFinishedAt());
        assertThat(first.getDuration()).isEqualTo(recordingEntity.getDuration());
        assertThat(first.getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(first.getCourt()).isEqualTo(courtEntity.getName());
        assertThat(first.getRegion().stream().findFirst().isPresent()).isTrue();
        assertThat(first.getRegion().stream().findFirst().get().getName()).isEqualTo(regionEntity.getName());
    }

    @DisplayName("Find counts for recordings per case an return a report list")
    @Test
    void reportRecordingsPerCaseSuccess() {
        var anotherCase = new Case();
        anotherCase.setId(UUID.randomUUID());
        anotherCase.setCourt(courtEntity);
        anotherCase.setReference("XYZ456");

        when(recordingRepository.countRecordingsPerCase())
            .thenReturn(List.of(new Object[] { caseEntity, (long) 1 }, new Object[]{ anotherCase, (long) 0 }));

        var report = reportService.reportRecordingsPerCase();

        assertThat(report.size()).isEqualTo(2);
        assertThat(report.get(0).getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.get(0).getCount()).isEqualTo(1);
        assertThat(report.get(1).getCaseReference()).isEqualTo(anotherCase.getReference());
        assertThat(report.get(1).getCount()).isEqualTo(0);

        assertThat(report.getFirst().getCourt()).isEqualTo(courtEntity.getName());
        assertThat(report
                       .getFirst()
                       .getRegions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getName()
        ).isEqualTo(regionEntity.getName());
    }

    @DisplayName("Find all edited recordings and return a report list")
    @Test
    void reportEditsSuccess() {
        recordingEntity.setVersion(2);
        var recording2 = new Recording();
        recording2.setId(UUID.randomUUID());
        recording2.setVersion(3);
        recording2.setCreatedAt(Timestamp.from(Instant.MIN));
        recording2.setCaptureSession(captureSessionEntity);

        when(recordingRepository.findAllByParentRecordingIsNotNull()).thenReturn(List.of(recording2, recordingEntity));

        var report = reportService.reportEdits();

        assertThat(report.size()).isEqualTo(2);

        assertThat(report.getFirst().getCreatedAt()).isEqualTo(recordingEntity.getCreatedAt());
        assertThat(report.getFirst().getVersion()).isEqualTo(recordingEntity.getVersion());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCourt()).isEqualTo(courtEntity.getName());
        assertThat(report
                       .getFirst()
                       .getRegions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getName()
        ).isEqualTo(regionEntity.getName());
        assertThat(report.getFirst().getRecordingId()).isEqualTo(recordingEntity.getId());

        assertThat(report.getLast().getRecordingId()).isEqualTo(recording2.getId());
    }

    @DisplayName("Find shared bookings and return report list")
    @Test
    void reportShared() {
        var shareWith = new User();
        shareWith.setId(UUID.randomUUID());
        shareWith.setEmail("example1@example.com");

        var shareBy = new User();
        shareBy.setId(UUID.randomUUID());
        shareBy.setEmail("example2@example.com");

        var sharedBooking1 = new ShareBooking();
        sharedBooking1.setCreatedAt(Timestamp.from(Instant.MIN));
        sharedBooking1.setBooking(bookingEntity);
        sharedBooking1.setSharedWith(shareWith);
        sharedBooking1.setSharedBy(shareBy);

        var sharedBooking2 = new ShareBooking();
        sharedBooking2.setCreatedAt(Timestamp.from(Instant.now()));
        sharedBooking2.setBooking(bookingEntity);
        sharedBooking2.setSharedWith(shareWith);
        sharedBooking2.setSharedBy(shareBy);

        when(shareBookingRepository.searchAll(null, null, null, null, false))
            .thenReturn(List.of(sharedBooking1, sharedBooking2));

        var report = reportService.reportShared(null, null, null, null);

        assertThat(report.size()).isEqualTo(2);

        assertThat(report.getFirst().getSharedAt()).isEqualTo(sharedBooking2.getCreatedAt());
        assertThat(report.getFirst().getAllocatedTo()).isEqualTo(sharedBooking2.getSharedWith().getEmail());
        assertThat(report.getFirst().getAllocatedBy()).isEqualTo(sharedBooking2.getSharedBy().getEmail());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCourt()).isEqualTo(courtEntity.getName());
        assertThat(report
                       .getFirst()
                       .getRegions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getName()
        ).isEqualTo(regionEntity.getName());
        assertThat(report.getFirst().getBookingId()).isEqualTo(sharedBooking2.getBooking().getId());

        assertThat(report.getLast().getSharedAt()).isEqualTo(sharedBooking1.getCreatedAt());
    }

    @DisplayName("Find all capture sessions with recording available and get report on booking details")
    @Test
    void reportScheduledSuccess() {
        var userEntity = new User();
        userEntity.setId(UUID.randomUUID());
        userEntity.setEmail("example@example.com");
        captureSessionEntity.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSessionEntity.setStartedByUser(userEntity);

        var otherBooking = new Booking();
        otherBooking.setId(UUID.randomUUID());
        otherBooking.setCaseId(caseEntity);
        otherBooking.setScheduledFor(Timestamp.from(Instant.MIN));

        captureSessionEntity.getBooking().setScheduledFor(Timestamp.from(Instant.MAX));

        var otherCaptureSessionEntity = new CaptureSession();
        otherCaptureSessionEntity.setId(UUID.randomUUID());
        otherCaptureSessionEntity.setBooking(otherBooking);
        otherCaptureSessionEntity.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        otherCaptureSessionEntity.setStartedByUser(userEntity);

        when(captureSessionRepository.findAllByStatus(RecordingStatus.RECORDING_AVAILABLE))
            .thenReturn(List.of(otherCaptureSessionEntity, captureSessionEntity));

        var report = reportService.reportScheduled();

        assertThat(report.size()).isEqualTo(2);
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getScheduledFor()).isEqualTo(captureSessionEntity.getBooking().getScheduledFor());
        assertThat(report.getFirst().getCaptureSessionUser()).isEqualTo(userEntity.getEmail());

        assertThat(report.get(1).getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.get(1).getScheduledFor()).isEqualTo(otherBooking.getScheduledFor());
    }

    @DisplayName("Find audits relating to playbacks from the portal and return a report")
    @Test
    void reportPlaybackPortalSuccess() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("example@example.com");
        user.setFirstName("Example");
        user.setLastName("Person");
        auditEntity.setCreatedBy(user.getId());
        auditEntity.setSource(AuditLogSource.PORTAL);

        var objectMapper = new ObjectMapper();
        var objectNode = objectMapper.createObjectNode();
        objectNode.put("recordingId", recordingEntity.getId().toString());
        auditEntity.setAuditDetails(objectNode);

        when(auditRepository
                 .findBySourceAndFunctionalAreaAndActivity(
                     AuditLogSource.PORTAL,
                     "Video Player",
                     "Play"
                 )
        ).thenReturn(List.of(auditEntity));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(recordingRepository.findById(recordingEntity.getId())).thenReturn(Optional.of(recordingEntity));

        var report = reportService.reportPlayback(AuditLogSource.PORTAL);

        assertThat(report.size()).isEqualTo(1);
        assertThat(report.getFirst().getPlaybackAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(report.getFirst().getUserEmail()).isEqualTo(user.getEmail());
        assertThat(report.getFirst().getUserFullName()).isEqualTo(user.getFullName());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCourt()).isEqualTo(courtEntity.getName());
        assertThat(report.getFirst().getRecordingId()).isEqualTo(recordingEntity.getId());
        assertThat(report
                       .getFirst()
                       .getRegions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getName()
        ).isEqualTo(regionEntity.getName());
    }

    @DisplayName("Find audits relating to playbacks from the portal and return a report using erroneous recordinguid")
    @Test
    void reportPlaybackPortalSuccessRecordingid() {
        auditEntity.setSource(AuditLogSource.PORTAL);

        var objectMapper = new ObjectMapper();
        var objectNode = objectMapper.createObjectNode();
        objectNode.put("recordinguid", recordingEntity.getId().toString());
        auditEntity.setAuditDetails(objectNode);

        when(auditRepository
                 .findBySourceAndFunctionalAreaAndActivity(
                     AuditLogSource.PORTAL,
                     "Video Player",
                     "Play"
                 )
        ).thenReturn(List.of(auditEntity));
        when(recordingRepository.findById(recordingEntity.getId())).thenReturn(Optional.of(recordingEntity));

        var report = reportService.reportPlayback(AuditLogSource.PORTAL);

        assertThat(report.size()).isEqualTo(1);
        assertThat(report.getFirst().getPlaybackAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(report.getFirst().getUserEmail()).isNullOrEmpty();
        assertThat(report.getFirst().getUserFullName()).isNullOrEmpty();
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCourt()).isEqualTo(courtEntity.getName());
        assertThat(report.getFirst().getRecordingId()).isEqualTo(recordingEntity.getId());
        assertThat(report
                       .getFirst()
                       .getRegions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getName()
        ).isEqualTo(regionEntity.getName());
    }

    @DisplayName("Find audits relating to playbacks from the portal and return a report without recordingid")
    @Test
    void reportPlaybackPortalSuccessNoRecordingId() {
        auditEntity.setSource(AuditLogSource.PORTAL);

        var objectMapper = new ObjectMapper();
        var objectNode = objectMapper.createObjectNode();
        auditEntity.setAuditDetails(objectNode);

        when(auditRepository
                 .findBySourceAndFunctionalAreaAndActivity(
                     AuditLogSource.PORTAL,
                     "Video Player",
                     "Play"
                 )
        ).thenReturn(List.of(auditEntity));
        when(recordingRepository.findById(recordingEntity.getId())).thenReturn(Optional.of(recordingEntity));

        var report = reportService.reportPlayback(AuditLogSource.PORTAL);

        assertThat(report.size()).isEqualTo(1);
        assertThat(report.getFirst().getPlaybackAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(report.getFirst().getUserEmail()).isNullOrEmpty();
        assertThat(report.getFirst().getUserFullName()).isNullOrEmpty();
        assertThat(report.getFirst().getCaseReference()).isNullOrEmpty();
        assertThat(report.getFirst().getCourt()).isNullOrEmpty();
        assertThat(report.getFirst().getRecordingId()).isNull();
    }

    @DisplayName("Find audits relating to playbacks from the portal and return a report without audit details")
    @Test
    void reportPlaybackPortalSuccessNoAuditDetails() {
        auditEntity.setSource(AuditLogSource.PORTAL);

        when(auditRepository
                 .findBySourceAndFunctionalAreaAndActivity(
                     AuditLogSource.PORTAL,
                     "Video Player",
                     "Play"
                 )
        ).thenReturn(List.of(auditEntity));
        var report = reportService.reportPlayback(AuditLogSource.PORTAL);

        assertThat(report.size()).isEqualTo(1);
        assertThat(report.getFirst().getPlaybackAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(report.getFirst().getUserEmail()).isNullOrEmpty();
        assertThat(report.getFirst().getUserFullName()).isNullOrEmpty();
        assertThat(report.getFirst().getCaseReference()).isNullOrEmpty(); // has a value???
        assertThat(report.getFirst().getCourt()).isNullOrEmpty();
        assertThat(report.getFirst().getRecordingId()).isNull();
    }

    @DisplayName("Find audits relating to playbacks from the application and return a report")
    @Test
    void reportPlaybackApplicationSuccess() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("example@example.com");
        user.setFirstName("Example");
        user.setLastName("Person");
        auditEntity.setCreatedBy(user.getId());
        auditEntity.setSource(AuditLogSource.APPLICATION);

        var objectMapper = new ObjectMapper();
        var objectNode = objectMapper.createObjectNode();
        objectNode.put("recordingId", recordingEntity.getId().toString());
        auditEntity.setAuditDetails(objectNode);

        when(auditRepository
                 .findBySourceAndFunctionalAreaAndActivity(
                     AuditLogSource.APPLICATION,
                     "View Recordings",
                     "Play"
                 )
        ).thenReturn(List.of(auditEntity));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(recordingRepository.findById(recordingEntity.getId())).thenReturn(Optional.of(recordingEntity));

        var report = reportService.reportPlayback(AuditLogSource.APPLICATION);

        assertThat(report.size()).isEqualTo(1);
        assertThat(report.getFirst().getPlaybackAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(report.getFirst().getUserEmail()).isEqualTo(user.getEmail());
        assertThat(report.getFirst().getUserFullName()).isEqualTo(user.getFullName());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCourt()).isEqualTo(courtEntity.getName());
        assertThat(report.getFirst().getRecordingId()).isEqualTo(recordingEntity.getId());
        assertThat(report
                       .getFirst()
                       .getRegions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getName()
        ).isEqualTo(regionEntity.getName());
    }

    @DisplayName("Find audits relating to all playback attempts and return a report")
    @Test
    void reportPlaybackAllSuccess() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("example@example.com");
        user.setFirstName("Example");
        user.setLastName("Person");
        auditEntity.setCreatedBy(user.getId());
        auditEntity.setSource(AuditLogSource.APPLICATION);
        var objectMapper = new ObjectMapper();
        var objectNode = objectMapper.createObjectNode();
        objectNode.put("recordingId", recordingEntity.getId().toString());
        auditEntity.setAuditDetails(objectNode);

        when(auditRepository.findAllAccessAttempts()).thenReturn(List.of(auditEntity));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(recordingRepository.findById(recordingEntity.getId())).thenReturn(Optional.of(recordingEntity));

        var report = reportService.reportPlayback(null);

        assertThat(report.size()).isEqualTo(1);
        assertThat(report.getFirst().getPlaybackAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(report.getFirst().getUserEmail()).isEqualTo(user.getEmail());
        assertThat(report.getFirst().getUserFullName()).isEqualTo(user.getFullName());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCourt()).isEqualTo(courtEntity.getName());
        assertThat(report.getFirst().getRecordingId()).isEqualTo(recordingEntity.getId());
        assertThat(report
                       .getFirst()
                       .getRegions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getName()
        ).isEqualTo(regionEntity.getName());

        verify(auditRepository, times(1)).findAllAccessAttempts();
        verify(auditRepository, never()).findBySourceAndFunctionalAreaAndActivity(any(), any(), any());
    }

    @DisplayName("Find audits relating to all playback attempts and return a report when null auditdetails")
    @Test
    void reportPlaybackAllSuccessNullAuditDetails() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("example@example.com");
        user.setFirstName("Example");
        user.setLastName("Person");
        auditEntity.setCreatedBy(user.getId());
        auditEntity.setSource(AuditLogSource.APPLICATION);

        when(auditRepository.findAllAccessAttempts()).thenReturn(List.of(auditEntity));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(recordingRepository.findById(recordingEntity.getId())).thenReturn(Optional.of(recordingEntity));

        var report = reportService.reportPlayback(null);

        assertThat(report.size()).isEqualTo(1);
        assertThat(report.getFirst().getPlaybackAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(report.getFirst().getUserEmail()).isEqualTo(user.getEmail());
        assertThat(report.getFirst().getUserFullName()).isEqualTo(user.getFullName());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(null);
        assertThat(report.getFirst().getCourt()).isEqualTo(null);
        assertThat(report.getFirst().getRecordingId()).isEqualTo(null);

        verify(auditRepository, times(1)).findAllAccessAttempts();
        verify(auditRepository, never()).findBySourceAndFunctionalAreaAndActivity(any(), any(), any());
    }

    @Test
    @DisplayName("Returns audits for all playback attempts a report when createdBy is appAccess id not of user id")
    void reportPlaybackAllSuccessAuditDetailsWhenAppAccessId() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("example@example.com");
        user.setFirstName("Example");
        user.setLastName("Person");
        var appAccess = new AppAccess();
        appAccess.setId(UUID.randomUUID());
        appAccess.setUser(user);
        auditEntity.setCreatedBy(appAccess.getId());
        auditEntity.setSource(AuditLogSource.APPLICATION);

        when(auditRepository.findAllAccessAttempts()).thenReturn(List.of(auditEntity));
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());
        when(appAccessRepository.findById(appAccess.getId())).thenReturn(Optional.of(appAccess));
        when(recordingRepository.findById(recordingEntity.getId())).thenReturn(Optional.of(recordingEntity));

        var report = reportService.reportPlayback(null);

        assertThat(report.size()).isEqualTo(1);
        assertThat(report.getFirst().getPlaybackAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(report.getFirst().getUserEmail()).isEqualTo(user.getEmail());
        assertThat(report.getFirst().getUserFullName()).isEqualTo(user.getFullName());

        verify(appAccessRepository, times(1)).findById(appAccess.getId());
        verify(auditRepository, times(1)).findAllAccessAttempts();
        verify(auditRepository, never()).findBySourceAndFunctionalAreaAndActivity(any(), any(), any());
    }

    @Test
    @DisplayName("Returns audits for all playback attempts a report when createdBy is portalAccess id not of user id")
    void reportPlaybackAllSuccessAuditDetailsWhenPortalAccessId() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("example@example.com");
        user.setFirstName("Example");
        user.setLastName("Person");
        var portalAccess = new PortalAccess();
        portalAccess.setId(UUID.randomUUID());
        portalAccess.setUser(user);
        auditEntity.setCreatedBy(portalAccess.getId());
        auditEntity.setSource(AuditLogSource.APPLICATION);

        when(auditRepository.findAllAccessAttempts()).thenReturn(List.of(auditEntity));
        when(userRepository.findById(user.getId())).thenReturn(Optional.empty());
        when(appAccessRepository.findById(portalAccess.getId())).thenReturn(Optional.empty());
        when(portalAccessRepository.findById(portalAccess.getId())).thenReturn(Optional.of(portalAccess));
        when(recordingRepository.findById(recordingEntity.getId())).thenReturn(Optional.of(recordingEntity));

        var report = reportService.reportPlayback(null);

        assertThat(report.size()).isEqualTo(1);
        assertThat(report.getFirst().getPlaybackAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(report.getFirst().getUserEmail()).isEqualTo(user.getEmail());
        assertThat(report.getFirst().getUserFullName()).isEqualTo(user.getFullName());

        verify(appAccessRepository, times(1)).findById(portalAccess.getId());
        verify(portalAccessRepository, times(1)).findById(portalAccess.getId());
        verify(auditRepository, times(1)).findAllAccessAttempts();
        verify(auditRepository, never()).findBySourceAndFunctionalAreaAndActivity(any(), any(), any());
    }

    @DisplayName("Find audits relating to playbacks from the source 'admin' and throw not found error")
    @Test
    void reportPlaybackAdminNotFound() {
        assertThrows(
            NotFoundException.class,
            () -> reportService.reportPlayback(AuditLogSource.ADMIN)
        );

        verify(auditRepository, never()).findBySourceAndFunctionalAreaAndActivity(any(), any(), any());
        verify(userRepository, never()).findById(any());
        verify(recordingRepository, never()).findById(any());
    }

    @DisplayName("Find audits relating to playbacks from the source 'auto' and throw not found error")
    @Test
    void reportPlaybackAutoNotFound() {
        assertThrows(
            NotFoundException.class,
            () -> reportService.reportPlayback(AuditLogSource.AUTO)
        );

        verify(auditRepository, never()).findBySourceAndFunctionalAreaAndActivity(any(), any(), any());
        verify(userRepository, never()).findById(any());
        verify(recordingRepository, never()).findById(any());
    }

    @DisplayName("Find a list of completed capture sessions")
    @Test
    void reportCompletedCaptureSessionsSuccess() {
        captureSessionEntity.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        final var witness = new Participant();
        witness.setId(UUID.randomUUID());
        witness.setParticipantType(ParticipantType.WITNESS);
        witness.setCaseId(caseEntity);
        final var defendant = new Participant();
        defendant.setId(UUID.randomUUID());
        defendant.setParticipantType(ParticipantType.DEFENDANT);
        defendant.setCaseId(caseEntity);

        bookingEntity.setParticipants(Set.of(witness, defendant));

        when(recordingRepository.findAllByParentRecordingIsNull()).thenReturn(List.of(recordingEntity));

        var report = reportService.reportCompletedCaptureSessions();

        assertThat(report.getFirst().getStartedAt()).isEqualTo(captureSessionEntity.getStartedAt());
        assertThat(report.getFirst().getFinishedAt()).isEqualTo(captureSessionEntity.getFinishedAt());
        assertThat(report.getFirst().getDuration()).isEqualTo(recordingEntity.getDuration());
        assertThat(report.getFirst().getScheduledFor()).isEqualTo(bookingEntity.getScheduledFor());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCountDefendants()).isEqualTo(1);
        assertThat(report.getFirst().getCountWitnesses()).isEqualTo(1);
        assertThat(report.getFirst().getRecordingStatus()).isEqualTo(captureSessionEntity.getStatus());
        assertThat(report.getFirst().getCourt()).isEqualTo(courtEntity.getName());
        assertThat(report
                       .getFirst()
                       .getRegions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getName()
        ).isEqualTo(regionEntity.getName());
    }

    @DisplayName("Find all share booking removals and return a report")
    @Test
    void reportAccessRemovedSuccess() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        var shareBooking = new ShareBooking();
        shareBooking.setId(UUID.randomUUID());
        shareBooking.setSharedWith(user);
        shareBooking.setBooking(bookingEntity);
        shareBooking.setDeletedAt(Timestamp.from(Instant.now()));

        when(shareBookingRepository.findAllByDeletedAtIsNotNull()).thenReturn(List.of(shareBooking));

        var report = reportService.reportAccessRemoved();

        assertThat(report.getFirst().getRemovedAt()).isEqualTo(shareBooking.getDeletedAt());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCourt()).isEqualTo(courtEntity.getName());
        assertThat(report
                       .getFirst()
                       .getRegions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getName()
        ).isEqualTo(regionEntity.getName());
        assertThat(report.getFirst().getUserFullName()).isEqualTo(user.getFirstName() + " " + user.getLastName());
        assertThat(report.getFirst().getUserEmail()).isEqualTo(user.getEmail());
        assertThat(report.getFirst().getRemovalReason()).isNull();
    }
}
