package uk.gov.hmcts.reform.preapi.batch.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.sql.Timestamp;
import java.util.UUID;


@Getter
@Setter
@Entity
@Table(name = "vf_migration_records")
public class MigrationRecord extends BaseEntity {

    @Column(name = "archive_id", nullable = false)
    private String archiveId;

    @Column(name = "archive_name", nullable = false)
    private String archiveName;

    @Column(name = "create_time")
    private Timestamp createTime;

    private Integer duration;

    @Column(name = "court_reference", length = 25)
    private String courtReference;

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
    private String mp4FileName;

    @Column(name = "file_size_mb")
    private String fileSizeMb;

    @Column(name = "recording_id")
    private UUID recordingId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private VfMigrationStatus status = VfMigrationStatus.PENDING;

    private String reason;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "resolved_at")
    private Timestamp resolvedAt;

    @Column(name = "created_at")
    private Timestamp createdAt;
}