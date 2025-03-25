package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.AuditAction;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

public class AuditServiceIT extends IntegrationTestBase {

    @Autowired
    private AuditService auditService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private CaptureSessionService captureSessionService;

    @Autowired
    private CourtService courtService;

    @Autowired
    private CaseService caseService;

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private UserService userService;

    private CreateCourtDTO getCreateCourt() {
        return HelperFactory.createCreateCourtDTO(CourtType.CROWN, "Foo Court", "1234");
    }

    private Court getCourt() {
        return HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
    }

    private Case getCase() {
        var court = getCourt();
        entityManager.persist(court);

        return HelperFactory.createCase(
            court,
            "ref1234",
            true,
            null);
    }

    private Booking getBooking() {

        var caseDTO = getCase();
        entityManager.persist(caseDTO);

        return HelperFactory.createBooking(
            caseDTO,
            Timestamp.from(Instant.now()),
            null
        );
    }

    private Booking getBooking(Case caseDTO) {

        return HelperFactory.createBooking(
            caseDTO,
            Timestamp.from(Instant.now()),
            null
        );
    }

    private CaptureSession getCaptureSession() {
        var booking = getBooking();
        entityManager.persist(booking);

        return HelperFactory.createCaptureSession(booking,
                                                  RecordingOrigin.PRE,
                                                  null,
                                                  null,
                                                  null,
                                                  null,
                                                  null,
                                                  null,
                                                  RecordingStatus.STANDBY,
                                                  null);
    }

    @Transactional
    @Test
    public void testInternalAudit() {
        var court = getCreateCourt();
        courtService.upsert(court);

        var auditResults = auditService.getAuditsByTableRecordId(court.getId());

        Assertions.assertEquals(2, auditResults.size());
        Assertions.assertEquals(AuditLogSource.AUTO, auditResults.get(0).getSource());
        Assertions.assertEquals(AuditAction.CREATE.toString(), auditResults.get(0).getActivity());
        Assertions.assertEquals(AuditAction.UPDATE.toString(), auditResults.get(1).getActivity()); // S28-2419

        court.setName("Bar Court");
        courtService.upsert(court);

        var updatedResults = auditService.getAuditsByTableRecordId(court.getId());
        Assertions.assertEquals(3, updatedResults.size());
        Assertions.assertEquals(AuditAction.CREATE.toString(), updatedResults.get(0).getActivity());
        Assertions.assertEquals(AuditAction.UPDATE.toString(), updatedResults.get(1).getActivity());
        Assertions.assertEquals(AuditAction.UPDATE.toString(), updatedResults.get(2).getActivity());

    }

    @Transactional
    @Test
    public void testDeleteAuditCase() {
        mockAdminUser();

        var caseDTO = getCase();
        var caseId = UUID.randomUUID();
        caseDTO.setId(caseId);

        var auditResultsEmpty = auditService.getAuditsByTableRecordId(caseDTO.getId());

        caseService.upsert(new CreateCaseDTO(caseDTO));
        var auditResultsCreated = auditService.getAuditsByTableRecordId(caseDTO.getId());
        caseService.deleteById(caseDTO.getId());

        var auditResults = auditService.getAuditsByTableRecordId(caseDTO.getId())
            .stream()
            .sorted(Comparator.comparing(Audit::getCreatedAt))
            .toList();
        Assertions.assertEquals(0, auditResultsEmpty.size());
        Assertions.assertEquals(1, auditResultsCreated.size());
        Assertions.assertEquals(2, auditResults.size());
        Assertions.assertEquals(AuditAction.CREATE.toString(), auditResults.get(0).getActivity());
        Assertions.assertEquals(AuditAction.DELETE.toString(), auditResults.get(1).getActivity());
    }

    @Transactional
    @Test
    public void testDeleteAuditBooking() {
        mockAdminUser();

        var caseDTO = getCase();
        entityManager.persist(caseDTO);

        var booking = getBooking(caseDTO);

        var bookingId = UUID.randomUUID();
        booking.setId(bookingId);

        var auditResultsEmpty = auditService.getAuditsByTableRecordId(booking.getId());

        bookingService.upsert(new CreateBookingDTO(booking));
        var auditResultsCreated = auditService.getAuditsByTableRecordId(booking.getId());
        bookingService.deleteCascade(caseDTO);

        var auditResults = auditService.getAuditsByTableRecordId(booking.getId());
        Assertions.assertEquals(0, auditResultsEmpty.size());
        Assertions.assertEquals(2, auditResultsCreated.size());
        Assertions.assertEquals(4, auditResults.size());
        Assertions.assertEquals(AuditAction.CREATE.toString(), auditResults.get(0).getActivity());
        Assertions.assertEquals(AuditAction.UPDATE.toString(), auditResults.get(1).getActivity());
        Assertions.assertEquals(AuditAction.UPDATE.toString(), auditResults.get(2).getActivity());
        Assertions.assertEquals(AuditAction.DELETE.toString(), auditResults.get(3).getActivity());
    }

    @Transactional
    @Test
    public void testDeleteAuditCaptureSession() {
        mockAdminUser();

        var booking = getBooking();
        entityManager.persist(booking);

        var captureSession = HelperFactory.createCaptureSession(booking,
                                                                RecordingOrigin.PRE,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                RecordingStatus.NO_RECORDING,
                                                                null);

        captureSession.setId(UUID.randomUUID());

        var auditResultsEmpty = auditService.getAuditsByTableRecordId(captureSession.getId());

        captureSessionService.upsert(new CreateCaptureSessionDTO(captureSession));
        var auditResultsCreated = auditService.getAuditsByTableRecordId(captureSession.getId());
        captureSessionService.deleteById(captureSession.getId());

        var auditResults = auditService.getAuditsByTableRecordId(captureSession.getId());
        Assertions.assertEquals(0, auditResultsEmpty.size());
        Assertions.assertEquals(1, auditResultsCreated.size());
        Assertions.assertEquals(2, auditResults.size());
        Assertions.assertEquals(AuditAction.CREATE.toString(), auditResults.get(0).getActivity());
        Assertions.assertEquals(AuditAction.DELETE.toString(), auditResults.get(1).getActivity());
    }

    @Transactional
    @Test
    public void testDeleteAuditRecording() {
        mockAdminUser();

        var captureSession = getCaptureSession();

        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(captureSession,
                                                      null,
                                                      1,
                                                      "test.mp4",
                                                      null);

        recording.setId(UUID.randomUUID());

        var auditResultsEmpty = auditService.getAuditsByTableRecordId(recording.getId());

        recordingService.upsert(new CreateRecordingDTO(recording));
        var auditResultsCreated = auditService.getAuditsByTableRecordId(recording.getId());
        recordingService.deleteById(recording.getId());

        var auditResults = auditService.getAuditsByTableRecordId(recording.getId());
        Assertions.assertEquals(0, auditResultsEmpty.size());
        Assertions.assertEquals(1, auditResultsCreated.size());
        Assertions.assertEquals(2, auditResults.size());
        Assertions.assertEquals(AuditAction.CREATE.toString(), auditResults.get(0).getActivity());
        Assertions.assertEquals(AuditAction.DELETE.toString(), auditResults.get(1).getActivity());
    }
}
