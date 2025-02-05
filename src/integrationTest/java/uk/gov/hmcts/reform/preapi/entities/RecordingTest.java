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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class RecordingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testSaveAndRetrieveRecordingVersion() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
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
            "TestIngressAddress",
            "TestLiveOutputAddress",
            new Timestamp(System.currentTimeMillis()),
            user,
            new Timestamp(System.currentTimeMillis()),
            user,
            RecordingStatus.RECORDING_AVAILABLE,
            new Timestamp(System.currentTimeMillis())
        );
        entityManager.persist(captureSession);

        Recording testRecording = new Recording();
        testRecording.setCaptureSession(captureSession);
        testRecording.setVersion(1);
        testRecording.setFilename("TestFilename");
        testRecording.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        testRecording.setEditInstruction("{\"instruction\":\"TestInstruction\"}");
        testRecording.setDuration(Duration.ofMinutes(5));
        testRecording.setDeletedAt(new Timestamp(System.currentTimeMillis()));
        entityManager.persist(testRecording);
        entityManager.flush();

        Recording retrievedRecording = entityManager.find(
            Recording.class,
            testRecording.getId()
        );

        assertEquals(testRecording.getId(), retrievedRecording.getId(), "Id should match");
        assertEquals(
            testRecording.getCaptureSession(),
            retrievedRecording.getCaptureSession(),
            "Capture session should match"
        );
        assertEquals(
            testRecording.getParentRecording(),
            retrievedRecording.getParentRecording(),
            "Parent recording should match"
        );
        assertEquals(testRecording.getVersion(), retrievedRecording.getVersion(), "Version should match");
        assertEquals(
            testRecording.getFilename(),
            retrievedRecording.getFilename(),
            "Filename should match"
        );
        assertEquals(
            testRecording.getCreatedAt(),
            retrievedRecording.getCreatedAt(),
            "Created at should match"
        );
        assertEquals(
            testRecording.getDuration(),
            retrievedRecording.getDuration(),
            "Duration should match"
        );
        assertEquals(
            testRecording.getEditInstruction(),
            retrievedRecording.getEditInstruction(),
            "Edit instructions should match"
        );
        assertEquals(
            testRecording.getDeletedAt(),
            retrievedRecording.getDeletedAt(),
            "Deleted at should match"
        );
    }
}
