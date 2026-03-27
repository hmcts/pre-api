package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.enums.ReencodeJobStatus;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "recording_reencode_jobs")
public class RecordingReencodeJob extends CreatedModifiedAtEntity {

    @Column(name = "recording_id", nullable = false)
    private UUID recordingId;

    @Column(name = "capture_session_id", nullable = false)
    private UUID captureSessionId;

    @Column(name = "migration_record_id", nullable = false)
    private UUID migrationRecordId;

    @Column(name = "source_container", nullable = false)
    private String sourceContainer;

    @Column(name = "source_blob_name", nullable = false)
    private String sourceBlobName;

    @Column(name = "reencoded_blob_name", nullable = false)
    private String reencodedBlobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "reencode_job_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ReencodeJobStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Timestamp startedAt;

    @Column(name = "finished_at")
    private Timestamp finishedAt;

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("recordingId", recordingId);
        details.put("captureSessionId", captureSessionId);
        details.put("migrationRecordId", migrationRecordId);
        details.put("sourceContainer", sourceContainer);
        details.put("sourceBlobName", sourceBlobName);
        details.put("reencodedBlobName", reencodedBlobName);
        details.put("status", status);
        details.put("errorMessage", errorMessage);
        details.put("startedAt", startedAt);
        details.put("finishedAt", finishedAt);
        return details;
    }
}
