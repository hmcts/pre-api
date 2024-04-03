package uk.gov.hmcts.reform.preapi.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ReportServiceIT extends IntegrationTestBase {
    @Autowired
    private ReportService reportService;

    @Transactional
    @Test
    public void reportSharedSuccess() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example court", "12458");
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
        assertReportSuccess(court, caseEntity, booking, user1, user2, share, reportFilterNone);

        var reportFilterCourt = reportService.reportShared(court.getId(), null, null, null);
        assertReportSuccess(court, caseEntity, booking, user1, user2, share, reportFilterCourt);

        var reportFilterBooking = reportService.reportShared(null, booking.getId(), null, null);
        assertReportSuccess(court, caseEntity, booking, user1, user2, share, reportFilterBooking);

        var reportFilterUserId = reportService.reportShared(null, null, user1.getId(), null);
        assertReportSuccess(court, caseEntity, booking, user1, user2, share, reportFilterUserId);

        var reportFilterUserEmail = reportService.reportShared(null, null, null, user1.getEmail());
        assertReportSuccess(court, caseEntity, booking, user1, user2, share, reportFilterUserEmail);

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

        var recording = HelperFactory.createRecording(captureSession, null, 1, null, "example.file", null);
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

    private void assertReportSuccess(Court court, Case caseEntity, Booking booking, User user1, User user2,
                                     ShareBooking share, List<SharedReportDTO> report) {
        assertThat(report.size()).isEqualTo(1);

        assertThat(report.getFirst().getSharedAt()).isEqualTo(share.getCreatedAt());
        assertThat(report.getFirst().getAllocatedTo()).isEqualTo(user1.getEmail());
        assertThat(report.getFirst().getAllocatedToFullName()).isEqualTo(user1.getFullName());
        assertThat(report.getFirst().getAllocatedBy()).isEqualTo(user2.getEmail());
        assertThat(report.getFirst().getAllocatedByFullName()).isEqualTo(user2.getFullName());
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getCourt()).isEqualTo(court.getName());
        assertThat(report.getFirst().getBookingId()).isEqualTo(booking.getId());
    }
}
