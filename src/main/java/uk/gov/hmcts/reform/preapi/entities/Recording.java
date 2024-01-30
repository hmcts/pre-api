package uk.gov.hmcts.reform.preapi.entities;


import io.hypersistence.utils.hibernate.type.interval.PostgreSQLIntervalType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;

@Getter
@Setter
@Entity
@Table(name = "recordings")
public class Recording extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "capture_session_id", referencedColumnName = "id")
    private CaptureSession captureSession;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_recording_id")
    private Recording parentRecording;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "url")
    private String url;

    @Column(name = "filename", nullable = false)
    private String filename;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Type(PostgreSQLIntervalType.class)
    @Column(
        name = "duration",
        columnDefinition = "interval"
    )
    private Duration duration;

    @Column(name = "edit_instruction")
    @JdbcTypeCode(SqlTypes.JSON)
    private String editInstruction;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @Transient
    private boolean deleted;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        return new HashMap<>() {
            {
                put("parentRecordingId", parentRecording != null ? parentRecording.getId() : null);
                put("version", version);
                put("filename", filename);
                if (duration != null) {
                    put("duration", duration.toString());
                }
                put("editInstruction", editInstruction);
                put("deleted", isDeleted());
            }
        };
    }
}
