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
import uk.gov.hmcts.reform.preapi.entities.RecordingVersion;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class RecordingVersionTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveRecordingVersion() {
        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", null);
        entityManager.persist(court);

        Case testCase = HelperFactory.createCase(court, "ref1234", true, false);
        entityManager.persist(testCase);

        Booking booking = HelperFactory.createBooking(testCase, new Date(System.currentTimeMillis()), false);
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

        RecordingVersion testRecordingVersion = new RecordingVersion();
        testRecordingVersion.setCaptureSession(captureSession);
        testRecordingVersion.setVersion(1);
        testRecordingVersion.setUrl("TestUrl");
        testRecordingVersion.setFilename("TestFilename");
        testRecordingVersion.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        testRecordingVersion.setEditInstruction("{\"instruction\":\"TestInstruction\"}");
        testRecordingVersion.setDuration(Time.valueOf("00:05:00"));
        testRecordingVersion.setDeleted(false);
        entityManager.persist(testRecordingVersion);
        entityManager.flush();

        RecordingVersion retrievedRecordingVersion = entityManager.find(
            RecordingVersion.class,
            testRecordingVersion.getId()
        );

        assertEquals(testRecordingVersion.getId(), retrievedRecordingVersion.getId(), "Id should match");
        assertEquals(
            testRecordingVersion.getCaptureSession(),
            retrievedRecordingVersion.getCaptureSession(),
            "Capture session should match"
        );
        assertEquals(testRecordingVersion.getVersion(), retrievedRecordingVersion.getVersion(), "Version should match");
        assertEquals(testRecordingVersion.getUrl(), retrievedRecordingVersion.getUrl(), "Url should match");
        assertEquals(
            testRecordingVersion.getFilename(),
            retrievedRecordingVersion.getFilename(),
            "Filename should match"
        );
        assertEquals(
            testRecordingVersion.getCreatedOn(),
            retrievedRecordingVersion.getCreatedOn(),
            "Created on should match"
        );
        assertEquals(
            testRecordingVersion.getDuration(),
            retrievedRecordingVersion.getDuration(),
            "Duration should match"
        );
        assertEquals(
            testRecordingVersion.getEditInstruction(),
            retrievedRecordingVersion.getEditInstruction(),
            "Edit instructions should match"
        );
        assertEquals(
            testRecordingVersion.isDeleted(),
            retrievedRecordingVersion.isDeleted(),
            "Deleted status should match"
        );
    }
}
