package uk.gov.hmcts.reform.preapi.batch.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.util.ArchiveNameSanitizer;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "vf_migration_records")
public class MigrationRecord extends BaseEntity implements IArchiveData {
    @Column(name = "archive_id", nullable = false)
    private String archiveId;

    @Column(name = "archive_name", nullable = false)
    private String archiveName;

    @Column(name = "create_time")
    private Timestamp createTime;

    @Column
    private Integer duration;

    @Column(name = "court_reference", length = 25)
    private String courtReference;


    @Column(name = "court_id")
    private UUID courtId;

    @Column(length = 11)
    private String urn;

    @Column(name = "exhibit_reference", length = 15)
    private String exhibitReference;

    @Column(name = "defendant_name", length = 100)
    private String defendantName;

    @Column(name = "witness_name", length = 100)
    private String witnessName;

    @Column(name = "recording_version", length = 4)
    private String recordingVersion;

    @Column(name = "recording_version_number", length = 1)
    private String recordingVersionNumber;

    @Column(name = "mp4_file_name", length = 10)
    private String fileName;

    @Column(name = "file_size_mb")
    private String fileSizeMb;

    @Column(name = "recording_id")
    private UUID recordingId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "capture_session_id")
    private UUID captureSessionId;

    @Column(name = "parent_temp_id")
    private UUID parentTempId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private VfMigrationStatus status = VfMigrationStatus.PENDING;

    @Column
    private String reason;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "resolved_at")
    private Timestamp resolvedAt;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "is_most_recent")
    private Boolean isMostRecent;

    @Column(name = "is_preferred")
    private Boolean isPreferred = true;

    @Column(name = "recording_group_key")
    private String recordingGroupKey;

    @Transient
    public String getSanitizedArchiveName() {
        return ArchiveNameSanitizer.sanitize(this.archiveName);
    }

    @Override
    public LocalDateTime getCreateTimeAsLocalDateTime() {
        return createTime != null ? createTime.toLocalDateTime() : null;
    }

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        HashMap<String, Object> details = new HashMap<>();
        details.put("archiveId", archiveId);
        details.put("status", status);
        details.put("archiveName", archiveName);
        details.put("createTime", createTime);
        details.put("duration", duration);
        details.put("courtReference", courtReference);
        details.put("urn", urn);
        details.put("exhibitReference", exhibitReference);
        details.put("defendantName", defendantName);
        details.put("witnessName", witnessName);
        details.put("recordingVersion", recordingVersion);
        details.put("recordingVersionNumber", recordingVersionNumber);
        details.put("fileName", fileName);
        details.put("fileSizeMb", fileSizeMb);
        details.put("recordingId", recordingId);
        details.put("bookingId", bookingId);
        details.put("captureSessionId", captureSessionId);
        details.put("parentTempId", parentTempId);
        details.put("reason", reason);
        details.put("errorMessage", errorMessage);
        details.put("resolvedAt", resolvedAt);
        details.put("isMostRecent", isMostRecent);
        details.put("isPreferred", isPreferred);
        details.put("recordingGroupKey", recordingGroupKey);
        return details;
    }
}
