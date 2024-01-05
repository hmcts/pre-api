package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class ShareRecordingDTOTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testSaveAndRetrieveShareRecording() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, new Timestamp(System.currentTimeMillis()));
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(
            testCase,
            new Timestamp(System.currentTimeMillis()),
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
            RecordingStatus.FINISHED,
            new Timestamp(System.currentTimeMillis())
        );
        entityManager.persist(captureSession);

        ShareRecording testShareRecording = new ShareRecording();
        testShareRecording.setCaptureSession(captureSession);
        testShareRecording.setSharedWith(user);
        testShareRecording.setSharedBy(user);
        testShareRecording.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        testShareRecording.setDeletedAt(new Timestamp(System.currentTimeMillis()));
        entityManager.persist(testShareRecording);
        entityManager.flush();

        ShareRecording retrievedShareRecording = entityManager.find(ShareRecording.class, testShareRecording.getId());

        assertEquals(testShareRecording.getId(), retrievedShareRecording.getId(), "Id should match");
        assertEquals(
            testShareRecording.getCaptureSession(),
            retrievedShareRecording.getCaptureSession(),
            "Capture session should match"
        );
        assertEquals(
            testShareRecording.getSharedWith(),
            retrievedShareRecording.getSharedWith(),
            "Shared with should match"
        );
        assertEquals(
            testShareRecording.getSharedBy(),
            retrievedShareRecording.getSharedBy(),
            "Shared by should match"
        );
        assertEquals(
            testShareRecording.getCreatedAt(),
            retrievedShareRecording.getCreatedAt(),
            "Created at should match"
        );
        assertEquals(
            testShareRecording.getDeletedAt(),
            retrievedShareRecording.getDeletedAt(),
            "Deleted at should match"
        );
    }
}
