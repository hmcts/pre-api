package uk.gov.hmcts.reform.entities;

import java.sql.Date;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

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
import uk.gov.hmcts.reform.preapi.entities.ShareRecording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

@SpringBootTest(classes = Application.class)
public class ShareRecordingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveShareRecording() {
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

        ShareRecording testShareRecording = new ShareRecording();
        testShareRecording.setCaptureSession(captureSession);
        testShareRecording.setSharedWith(user);
        testShareRecording.setSharedBy(user);
        testShareRecording.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        testShareRecording.setDeleted(false);
        entityManager.persist(testShareRecording);
        entityManager.flush();

        ShareRecording retrievedShareRecording = entityManager.find(ShareRecording.class, testShareRecording.getId());

        assertEquals(testShareRecording.getId(), retrievedShareRecording.getId());
        assertEquals(testShareRecording.getCaptureSession(), retrievedShareRecording.getCaptureSession());
        assertEquals(testShareRecording.getSharedWith(), retrievedShareRecording.getSharedWith());
        assertEquals(testShareRecording.getSharedBy(), retrievedShareRecording.getSharedBy());
        assertEquals(testShareRecording.getCreatedOn(), retrievedShareRecording.getCreatedOn());
        assertEquals(testShareRecording.isDeleted(), retrievedShareRecording.isDeleted());
        assertEquals(testShareRecording.getCreatedOn(), retrievedShareRecording.getCreatedOn());
    }
}
