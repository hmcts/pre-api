package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.sql.Time;
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
import uk.gov.hmcts.reform.preapi.entities.RecordingVersion;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

@SpringBootTest(classes = Application.class)
public class RecordingVersionTest {

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

        CaptureSession captureSession = HelperFactory.createCaptureSession(booking, RecordingOrigin.pre, "TestIngrestAddress", "TestLiveOutputAddress", new Timestamp(System.currentTimeMillis()), user, new Timestamp(System.currentTimeMillis()), user, RecordingStatus.finished, false);
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

        RecordingVersion retrievedRecordingVersion = entityManager.find(RecordingVersion.class, testRecordingVersion.getId());

        assertEquals(testRecordingVersion.getId(), retrievedRecordingVersion.getId());
        assertEquals(testRecordingVersion.getCaptureSession(), retrievedRecordingVersion.getCaptureSession());
        assertEquals(testRecordingVersion.getVersion(), retrievedRecordingVersion.getVersion());
        assertEquals(testRecordingVersion.getUrl(), retrievedRecordingVersion.getUrl());
        assertEquals(testRecordingVersion.getFilename(), retrievedRecordingVersion.getFilename());
        assertEquals(testRecordingVersion.getCreatedOn(), retrievedRecordingVersion.getCreatedOn());
        assertEquals(testRecordingVersion.getDuration(), retrievedRecordingVersion.getDuration());
        assertEquals(testRecordingVersion.getEditInstruction(), retrievedRecordingVersion.getEditInstruction());
        assertEquals(testRecordingVersion.isDeleted(), retrievedRecordingVersion.isDeleted());
        assertEquals(testRecordingVersion.getCreatedOn(), retrievedRecordingVersion.getCreatedOn());
    }
}
