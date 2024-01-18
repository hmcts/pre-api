package uk.gov.hmcts.reform.preapi.services;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;

@SpringBootTest(classes = Application.class)
public class CaseServiceIT {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private CaseService caseService;

    @Transactional
    @Test
    public void testCascadeDelete() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
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
            null,
            null
        );
        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(captureSession, null, 1, "url", "filename",null);
        entityManager.persist(recording);

        caseService.deleteById(caseEntity.getId());

        entityManager.refresh(caseEntity);
        entityManager.refresh(booking);
        entityManager.refresh(captureSession);
        entityManager.refresh(recording);

        Assertions.assertNotNull(caseEntity.getDeletedAt());
        Assertions.assertNotNull(booking.getDeletedAt());
        Assertions.assertNotNull(captureSession.getDeletedAt());
        Assertions.assertNotNull(recording.getDeletedAt());
    }
}
