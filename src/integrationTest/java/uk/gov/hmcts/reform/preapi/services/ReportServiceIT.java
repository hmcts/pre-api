package uk.gov.hmcts.reform.preapi.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.dto.reports.ConcurrentCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ReportServiceIT extends IntegrationTestBase {
    @Autowired
    private ReportService reportService;

    @Test
    @Transactional
    public void reportSharedAllRecordingIdNull() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example court", "12458");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking);

        var captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(captureSession, null, 1, "example.file", null);
        entityManager.persist(recording);

        var user = HelperFactory.createUser("Example", "One", "example1@example.com", null, null, null);
        entityManager.persist(user);

        var audit = new Audit();
        audit.setId(UUID.randomUUID());
        audit.setActivity("Play");
        audit.setCreatedBy(user.getId());
        audit.setSource(AuditLogSource.APPLICATION);
        ObjectMapper mapper = new ObjectMapper();
        audit.setAuditDetails(mapper.valueToTree(new HashMap<String, String>() {{
                put("description", "Playback on recording has started");
                put("recordingId", null);
            }}
        ));
        entityManager.persist(audit);

        var response = reportService.reportPlayback(null);
        assertThat(response).isNotNull();
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.getFirst().getRecordingId()).isNull();
    }

    @Test
    @Transactional
    public void reportSharedSuccess() {
        var region = HelperFactory.createRegion("Example Region", Set.of());
        entityManager.persist(region);

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example court", "12458");
        court.setRegions(Set.of(region));
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking);

        var user1 = HelperFactory.createUser("Example", "One", "example1@example.com", null, null, null);
        entityManager.persist(user1);

        var user2 = HelperFactory.createUser("Example", "Two", "example2@example.com", null, null, null);
        entityManager.persist(user2);

        var share = HelperFactory.createShareBooking(user1, user2, booking, null);
        entityManager.persist(share);

        var reportFilterNone = reportService.reportShared(null, null, null, null);
        assertSharedReportSuccess(court, caseEntity, user1, user2, share, reportFilterNone);

        var reportFilterCourt = reportService.reportShared(court.getId(), null, null, null);
        assertSharedReportSuccess(court, caseEntity, user1, user2, share, reportFilterCourt);

        var reportFilterBooking = reportService.reportShared(null, booking.getId(), null, null);
        assertSharedReportSuccess(court, caseEntity, user1, user2, share, reportFilterBooking);

        var reportFilterUserId = reportService.reportShared(null, null, user1.getId(), null);
        assertSharedReportSuccess(court, caseEntity, user1, user2, share, reportFilterUserId);

        var reportFilterUserEmail = reportService.reportShared(null, null, null, user1.getEmail());
        assertSharedReportSuccess(court, caseEntity, user1, user2, share, reportFilterUserEmail);

        var reportFilterNotFound1 = reportService.reportShared(UUID.randomUUID(), null, null, null);
        assertThat(reportFilterNotFound1.isEmpty()).isTrue();

        var reportFilterNotFound2 = reportService.reportShared(null, UUID.randomUUID(), null, null);
        assertThat(reportFilterNotFound2.isEmpty()).isTrue();

        var reportFilterNotFound3 = reportService.reportShared(null, null, UUID.randomUUID(), null);
        assertThat(reportFilterNotFound3.isEmpty()).isTrue();

        var reportFilterNotFound4 = reportService.reportShared(null, null, null, "test@test.com");
        assertThat(reportFilterNotFound4.isEmpty()).isTrue();
    }

    @Transactional
    @Test
    void reportRecordingParticipantsSuccess() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example court", "12458");
        entityManager.persist(court);

        var aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);

        var participant1 = HelperFactory.createParticipant(aCase, ParticipantType.DEFENDANT, "ONE", "ONE", null);
        entityManager.persist(participant1);

        var participant2 = HelperFactory.createParticipant(aCase, ParticipantType.WITNESS, "TWO", "TWO", null);
        entityManager.persist(participant2);

        var booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null);
        booking.setParticipants(Set.of(participant1, participant2));
        entityManager.persist(booking);

        var captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.from(Instant.now()),
            null,
            null,
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(captureSession, null, 1, "example.file", null);
        entityManager.persist(recording);

        var response = reportService.reportRecordingParticipants();
        assertThat(response).isNotNull();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.getFirst().getRecordedAt()).isEqualTo(captureSession.getStartedAt());
        assertThat(response.getFirst().getCourtName()).isEqualTo(court.getName());
        assertThat(response.getFirst().getCaseReference()).isEqualTo(aCase.getReference());
        assertThat(response.getFirst().getRecordingId()).isEqualTo(recording.getId());

        assertThat(response.stream().anyMatch(
            r -> r.getParticipantName().equals(participant1.getFirstName() + " " + participant1.getLastName())
                && r.getParticipantType().equals(participant1.getParticipantType())))
            .isTrue();
        assertThat(response.stream().anyMatch(
            r -> r.getParticipantName().equals(participant2.getFirstName() + " " + participant2.getLastName())
                && r.getParticipantType().equals(participant2.getParticipantType())))
            .isTrue();
    }

    @Transactional
    @Test
    void reportRecordingsPerCaseSuccess() {
        var region = HelperFactory.createRegion("Example Region", Set.of());
        entityManager.persist(region);

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example court", "12458");
        court.setRegions(Set.of(region));
        entityManager.persist(court);

        var aCase1 = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase1);
        var aCase2 = HelperFactory.createCase(court, "CASE67890", true, null);
        entityManager.persist(aCase2);

        var booking1 = HelperFactory.createBooking(aCase1, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking1);
        var booking2 = HelperFactory.createBooking(aCase1, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking2);
        var booking3 = HelperFactory.createBooking(aCase2, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking3);

        var captureSession1 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.from(Instant.now()),
            null,
            Timestamp.from(Instant.now()),
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession1);
        var captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.from(Instant.now()),
            null,
            Timestamp.from(Instant.now()),
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession2);
        var captureSession3 = HelperFactory.createCaptureSession(
            booking3,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.from(Instant.now()),
            null,
            Timestamp.from(Instant.now()),
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession3);

        var recording1 = HelperFactory.createRecording(captureSession1, null, 1, "example.file", null);
        entityManager.persist(recording1);
        var recording2 = HelperFactory.createRecording(captureSession2, null, 1, "example.file", null);
        entityManager.persist(recording2);
        var recording3 = HelperFactory.createRecording(captureSession3, null, 1, "example.file", null);
        entityManager.persist(recording3);
        var recording4 = HelperFactory.createRecording(captureSession3, recording3, 2, "example.file", null);
        entityManager.persist(recording4);

        var report = reportService.reportRecordingsPerCase();

        assertThat(report).isNotNull();
        assertThat(report.size()).isEqualTo(2);

        var recordingsForCase1 =  report
            .stream()
            .filter(r -> r.getCaseReference().equals(aCase1.getReference()))
            .findFirst();
        assertThat(recordingsForCase1).isPresent();
        assertRecordingPerCaseSuccess(recordingsForCase1.get(), aCase1, court, region, 2);

        var recordingsForCase2 =  report
            .stream()
            .filter(r -> r.getCaseReference().equals(aCase2.getReference()))
            .findFirst();
        assertThat(recordingsForCase2).isPresent();
        assertRecordingPerCaseSuccess(recordingsForCase2.get(), aCase2, court, region, 1);
    }

    @Transactional
    @Test
    void reportConcurrentCaptureSessionsSuccess() {
        var region = HelperFactory.createRegion("Example Region", Set.of());
        entityManager.persist(region);

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example court", "12458");
        court.setRegions(Set.of(region));
        entityManager.persist(court);

        var aCase = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(aCase);

        var booking1 = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking1);

        var booking2 = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now()), null);
        entityManager.persist(booking2);

        var captureSession1 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.from(Instant.now()),
            null,
            Timestamp.from(Instant.now()),
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession1);
        var captureSession2 = HelperFactory.createCaptureSession(
            booking2,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.from(Instant.now()),
            null,
            null,
            null,
            RecordingStatus.RECORDING,
            null
        );
        entityManager.persist(captureSession2);
        var captureSession3 = HelperFactory.createCaptureSession(
            booking1,
            RecordingOrigin.PRE,
            null,
            null,
            Timestamp.from(Instant.now()),
            null,
            Timestamp.from(Instant.now()),
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            Timestamp.from(Instant.now())
        );
        entityManager.persist(captureSession3);

        var recording1 = HelperFactory.createRecording(captureSession1, null, 1, "example.file", null);
        recording1.setDuration(Duration.ofMinutes(3));
        captureSession1.setRecordings(Set.of(recording1));
        entityManager.persist(recording1);
        entityManager.persist(captureSession1);
        var recording3 = HelperFactory.createRecording(
            captureSession3,
            null,
            1,
            "example.file",
            Timestamp.from(Instant.now())
        );
        recording3.setDuration(Duration.ofMinutes(30));
        entityManager.persist(recording3);

        var report = reportService.reportCaptureSessions();
        assertThat(report).isNotNull();
        assertThat(report.size()).isEqualTo(2);

        var report1 = report.stream()
            .filter(r -> r.getDuration() != null).findFirst();
        assertThat(report1).isPresent();
        assertConcurrentCaptureSessionsSuccess(report1.get(), court, region, aCase, captureSession1, recording1);

        var report2 = report.stream().filter(r -> r.getDuration() == null).findFirst();
        assertThat(report2).isPresent();
        assertConcurrentCaptureSessionsSuccess(report2.get(), court, region, aCase, captureSession2, null);

        assertThat(report.stream().anyMatch(r -> r.getDuration() != null && r.getDuration().toMinutes() == 30))
            .isFalse();
    }

    private void assertConcurrentCaptureSessionsSuccess(ConcurrentCaptureSessionReportDTO report,
                                                        Court court,
                                                        Region region,
                                                        Case aCase,
                                                        CaptureSession captureSession,
                                                        Recording recording) {
        assertThat(report).isNotNull();
        assertThat(report.getDate()).isEqualTo(DateTimeUtils.formatDate(captureSession.getStartedAt()));
        assertThat(report.getStartTime()).isEqualTo(DateTimeUtils.formatTime(captureSession.getStartedAt()));
        assertThat(report.getEndTime())
            .isEqualTo(captureSession.getFinishedAt() != null
                           ? DateTimeUtils.formatTime(captureSession.getFinishedAt())
                           : null);
        assertThat(report.getDuration()).isEqualTo((recording == null ? null : recording.getDuration()));
        assertThat(report.getCaseReference()).isEqualTo(aCase.getReference());
        assertThat(report.getCourt()).isEqualTo(court.getName());
        assertThat(report.getRegion()).isEqualTo(region.getName());
    }

    private void assertRecordingPerCaseSuccess(RecordingsPerCaseReportDTO report,
                                               Case aCase,
                                               Court court,
                                               Region region,
                                               int expectedCount) {
        assertThat(report.getCaseReference()).isEqualTo(aCase.getReference());
        assertThat(report.getCourt()).isEqualTo(court.getName());
        assertThat(report.getRegions().size()).isEqualTo(1);
        assertThat(report.getRegions().stream().findFirst().get().getName())
            .isEqualTo(region.getName());
        assertThat(report.getCount()).isEqualTo(expectedCount);
    }

    private void assertSharedReportSuccess(Court court, Case caseEntity, User user1, User user2,
                                           ShareBooking share, List<SharedReportDTO> report) {
        assertThat(report.size()).isEqualTo(1);

        assertThat(report.getFirst().getShareDate()).isEqualTo(DateTimeUtils.formatDate(share.getCreatedAt()));
        assertThat(report.getFirst().getShareTime()).isEqualTo(DateTimeUtils.formatTime(share.getCreatedAt()));
        assertThat(report.getFirst().getTimezone())
            .isEqualTo(DateTimeUtils.getTimezoneAbbreviation(share.getCreatedAt()));
        assertThat(report.getFirst().getSharedWith()).isEqualTo(user1.getEmail());
        assertThat(report.getFirst().getSharedWithFullName()).isEqualTo(user1.getFullName());
        assertThat(report.getFirst().getGrantedBy()).isEqualTo(user2.getEmail());
        assertThat(report.getFirst().getGrantedByFullName()).isEqualTo(user2.getFullName());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCourtName()).isEqualTo(court.getName());
        assertThat(report.getFirst().getCounty()).isEqualTo(court.getCounty());
        assertThat(report.getFirst().getPostcode()).isEqualTo(court.getPostcode());
        assertThat(report.getFirst().getRegion()).isEqualTo(court.getRegions().stream().findFirst().get().getName());
    }
}
