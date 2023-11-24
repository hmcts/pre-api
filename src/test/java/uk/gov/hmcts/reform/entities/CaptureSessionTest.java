
package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.sql.Timestamp;

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

@SpringBootTest(classes = Application.class)
public class CaptureSessionTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveCaptureSession() {
        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, false);
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(testCase, new Date(System.currentTimeMillis()), false);
        entityManager.persist(booking);

        User user = HelperFactory.createUser("Test", "User", "example@example.com", false, null, null);
        entityManager.persist(user);

        CaptureSession captureSession = HelperFactory.createCaptureSession(booking, RecordingOrigin.pre, "TestIngrestAddress", "TestLiveOutputAddress", new Timestamp(System.currentTimeMillis()), user, new Timestamp(System.currentTimeMillis()), user, RecordingStatus.finished, false);
        entityManager.persist(captureSession);
        entityManager.flush();

        CaptureSession retrievedCaptureSession = entityManager.find(CaptureSession.class, captureSession.getId());

        assertEquals(captureSession.getId(), retrievedCaptureSession.getId());
        assertEquals(captureSession.getBooking(), retrievedCaptureSession.getBooking());
        assertEquals(captureSession.getOrigin(), retrievedCaptureSession.getOrigin());
        assertEquals(captureSession.getIngestAddress(), retrievedCaptureSession.getIngestAddress());
        assertEquals(captureSession.getLiveOutputUrl(), retrievedCaptureSession.getLiveOutputUrl());
        assertEquals(captureSession.getStartedOn(), retrievedCaptureSession.getStartedOn());
        assertEquals(captureSession.getStartedByUser(), retrievedCaptureSession.getStartedByUser());
        assertEquals(captureSession.getFinishedOn(), retrievedCaptureSession.getFinishedOn());
        assertEquals(captureSession.getFinishedByUserId(), retrievedCaptureSession.getFinishedByUserId());
        assertEquals(captureSession.getStatus(), retrievedCaptureSession.getStatus());
        assertEquals(captureSession.isDeleted(), retrievedCaptureSession.isDeleted());
    }
}
