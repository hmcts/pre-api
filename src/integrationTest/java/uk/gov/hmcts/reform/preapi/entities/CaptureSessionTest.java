package uk.gov.hmcts.reform.preapi.entities;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class CaptureSessionTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveCaptureSession() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, new Timestamp(System.currentTimeMillis()));
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(
            testCase,
            Timestamp.valueOf(LocalDateTime.now()),
            new Timestamp(System.currentTimeMillis())
        );
        entityManager.persist(booking);

        User user = HelperFactory.createDefaultTestUser();
        entityManager.persist(user);

        CaptureSession captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            "TestIngrestAddress",
            "TestLiveOutputAddress",
            new Timestamp(System.currentTimeMillis()),
            user,
            new Timestamp(System.currentTimeMillis()),
            user,
            RecordingStatus.RECORDING_AVAILABLE,
            new Timestamp(System.currentTimeMillis())
        );
        entityManager.persist(captureSession);
        entityManager.flush();

        CaptureSession retrievedCaptureSession = entityManager.find(CaptureSession.class, captureSession.getId());

        assertEquals(captureSession.getId(), retrievedCaptureSession.getId(), "Id should match");
        assertEquals(captureSession.getBooking(), retrievedCaptureSession.getBooking(), "BookingDTO should match");
        assertEquals(captureSession.getOrigin(), retrievedCaptureSession.getOrigin(), "Origin should match");
        assertEquals(
            captureSession.getIngestAddress(),
            retrievedCaptureSession.getIngestAddress(),
            "Ingest address should match"
        );
        assertEquals(
            captureSession.getLiveOutputUrl(),
            retrievedCaptureSession.getLiveOutputUrl(),
            "Live output url should match"
        );
        assertEquals(captureSession.getStartedAt(), retrievedCaptureSession.getStartedAt(), "Started at should match");
        assertEquals(
            captureSession.getStartedByUser(),
            retrievedCaptureSession.getStartedByUser(),
            "Started by by user should match"
        );
        assertEquals(
            captureSession.getFinishedAt(),
            retrievedCaptureSession.getFinishedAt(),
            "Finished at should match"
        );
        assertEquals(
            captureSession.getFinishedByUser(),
            retrievedCaptureSession.getFinishedByUser(),
            "Finished by user should match"
        );
        assertEquals(captureSession.getStatus(), retrievedCaptureSession.getStatus(), "Status should match");
        assertEquals(captureSession.getDeletedAt(), retrievedCaptureSession.getDeletedAt(), "Deleted at should match");
    }
}
