package uk.gov.hmcts.reform.preapi.batch.entities;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationRecordTest {

    @Test
    void testGettersAndSetters() {
        MigrationRecord record = new MigrationRecord();
        UUID uuid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2023, 5, 1, 10, 0);
        Timestamp timestamp = Timestamp.valueOf(now);

        record.setArchiveId("123");
        record.setArchiveName("Test-File.mp4");
        record.setCreateTime(timestamp);
        record.setDuration(120);
        record.setCourtReference("COURT001");
        record.setCourtId(uuid);
        record.setUrn("URN123");
        record.setExhibitReference("EX123");
        record.setDefendantName("Doe");
        record.setWitnessName("Smith");
        record.setRecordingVersion("ORIG");
        record.setRecordingVersionNumber("1");
        record.setFileName("file.mp4");
        record.setFileSizeMb("5MB");
        record.setRecordingId(uuid);
        record.setBookingId(uuid);
        record.setCaptureSessionId(uuid);
        record.setParentTempId(uuid);
        record.setStatus(VfMigrationStatus.SUCCESS);
        record.setReason("Completed");
        record.setErrorMessage("None");
        record.setResolvedAt(timestamp);
        record.setCreatedAt(timestamp);
        record.setIsMostRecent(true);
        record.setIsPreferred(true);
        record.setRecordingGroupKey("ref-group-key");

        assertEquals("123", record.getArchiveId());
        assertEquals("Test-File.mp4", record.getArchiveName());
        assertEquals(now, record.getCreateTimeAsLocalDateTime());
        assertEquals("file.mp4", record.getFileName());
        assertEquals("Test-File.mp4", record.getSanitizedArchiveName());
        assertEquals(VfMigrationStatus.SUCCESS, record.getStatus());
        assertTrue(record.getIsPreferred());
        assertTrue(record.getIsMostRecent());
    }

    @Test
    void testSanitizedArchiveName() {
        MigrationRecord record = new MigrationRecord();
        record.setArchiveName("OLD_CP-Court File.mp4");
        assertEquals("CourtFile.mp4", record.getSanitizedArchiveName());
    }

    @Test
    void testGetCreateTimeAsLocalDateTime_Null() {
        MigrationRecord record = new MigrationRecord();
        assertNull(record.getCreateTimeAsLocalDateTime());
    }
}