package uk.gov.hmcts.reform.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class CaptureSessionTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveCaptureSession() {
        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, false);
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(testCase, Timestamp.valueOf(LocalDateTime.now()), false);
        entityManager.persist(booking);

        User user = HelperFactory.createUser("Test", "User", "example@example.com", false, null, null);
        entityManager.persist(user);

        CaptureSession captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.pre,
            "TestIngrestAddress",
            "TestLiveOutputAddress",
            new Timestamp(System.currentTimeMillis()),
            user,
            new Timestamp(System.currentTimeMillis()),
            user,
            RecordingStatus.finished,
            false
        );
        entityManager.persist(captureSession);
        entityManager.flush();

        CaptureSession retrievedCaptureSession = entityManager.find(CaptureSession.class, captureSession.getId());

        assertEquals(captureSession.getId(), retrievedCaptureSession.getId(), "Id should match");
        assertEquals(captureSession.getBooking(), retrievedCaptureSession.getBooking(), "Booking should match");
        assertEquals(
            captureSession.getParentRecording(),
            retrievedCaptureSession.getParentRecording(),
            "Parent recording should match"
        );
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
        assertEquals(captureSession.getStartedOn(), retrievedCaptureSession.getStartedOn(), "Started on should match");
        assertEquals(
            captureSession.getStartedByUser(),
            retrievedCaptureSession.getStartedByUser(),
            "Started on by user should match"
        );
        assertEquals(
            captureSession.getFinishedOn(),
            retrievedCaptureSession.getFinishedOn(),
            "Finished on should match"
        );
        assertEquals(
            captureSession.getFinishedByUserId(),
            retrievedCaptureSession.getFinishedByUserId(),
            "Finished on by user should match"
        );
        assertEquals(captureSession.getStatus(), retrievedCaptureSession.getStatus(), "Status should match");
        assertEquals(captureSession.isDeleted(), retrievedCaptureSession.isDeleted(), "Deleted status should match");
    }
}
