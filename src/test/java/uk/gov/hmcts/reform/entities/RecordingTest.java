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
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Time;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class RecordingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveRecordingVersion() {
        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, false);
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(testCase, new Timestamp(System.currentTimeMillis()), false);
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

        Recording testRecording = new Recording();
        testRecording.setCaptureSession(captureSession);
        testRecording.setVersion(1);
        testRecording.setUrl("TestUrl");
        testRecording.setFilename("TestFilename");
        testRecording.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        testRecording.setEditInstruction("{\"instruction\":\"TestInstruction\"}");
        testRecording.setDuration(Time.valueOf("00:05:00"));
        testRecording.setDeleted(false);
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
        assertEquals(testRecording.getVersion(), retrievedRecording.getVersion(), "Version should match");
        assertEquals(testRecording.getUrl(), retrievedRecording.getUrl(), "Url should match");
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
            testRecording.isDeleted(),
            retrievedRecording.isDeleted(),
            "Deleted status should match"
        );
    }
}
