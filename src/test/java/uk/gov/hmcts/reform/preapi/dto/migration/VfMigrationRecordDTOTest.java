package uk.gov.hmcts.reform.preapi.dto.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class VfMigrationRecordDTOTest {
    @Test
    @DisplayName("Should correctly map fields from MigrationRecord entity to VfMigrationRecordDTO")
    void testConstructorMapping() {
        UUID id = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        Timestamp createTime = Timestamp.from(Instant.now());
        UUID recordingId = UUID.randomUUID();
        Timestamp resolvedAt = Timestamp.from(Instant.now().plusSeconds(1));
        Timestamp createdAt = Timestamp.from(Instant.now().minusSeconds(1));

        MigrationRecord entity = new MigrationRecord();
        entity.setId(id);
        entity.setArchiveId("archiveId");
        entity.setArchiveName("archiveName");
        entity.setCreateTime(createTime);
        entity.setDuration(120);
        entity.setCourtId(courtId);
        entity.setUrn("URN123456");
        entity.setExhibitReference("EXHIBIT123");
        entity.setDefendantName("John Doe");
        entity.setWitnessName("Jane Doe");
        entity.setRecordingVersion("v1.0");
        entity.setRecordingVersionNumber("1.0.1");
        entity.setMp4FileName("recording.mp4");
        entity.setFileSizeMb("100MB");
        entity.setRecordingId(recordingId);
        entity.setStatus(VfMigrationStatus.SUCCESS);
        entity.setReason("Test reason");
        entity.setErrorMessage("No error");
        entity.setResolvedAt(resolvedAt);
        entity.setCreatedAt(createdAt);

        VfMigrationRecordDTO dto = new VfMigrationRecordDTO(entity);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getArchiveId()).isEqualTo("archiveId");
        assertThat(dto.getArchiveName()).isEqualTo("archiveName");
        assertThat(dto.getCreateTime()).isEqualTo(createTime);
        assertThat(dto.getDuration()).isEqualTo(120);
        assertThat(dto.getCourtId()).isEqualTo(courtId);
        assertThat(dto.getUrn()).isEqualTo("URN123456");
        assertThat(dto.getExhibitReference()).isEqualTo("EXHIBIT123");
        assertThat(dto.getDefendantName()).isEqualTo("John Doe");
        assertThat(dto.getWitnessName()).isEqualTo("Jane Doe");
        assertThat(dto.getRecordingVersion()).isEqualTo("v1.0");
        assertThat(dto.getRecordingVersionNumber()).isEqualTo("1.0.1");
        assertThat(dto.getFilename()).isEqualTo("recording.mp4");
        assertThat(dto.getFileSize()).isEqualTo("100MB");
        assertThat(dto.getRecordingId()).isEqualTo(recordingId);
        assertThat(dto.getStatus()).isEqualTo(VfMigrationStatus.SUCCESS);
        assertThat(dto.getReason()).isEqualTo("Test reason");
        assertThat(dto.getErrorMessage()).isEqualTo("No error");
        assertThat(dto.getResolvedAt()).isEqualTo(resolvedAt);
        assertThat(dto.getCreatedAt()).isEqualTo(createdAt);
    }
}
