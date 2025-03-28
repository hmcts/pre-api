package uk.gov.hmcts.reform.preapi.entities;

import io.hypersistence.utils.hibernate.type.interval.PostgreSQLIntervalType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.entities.base.ISoftDeletable;
import uk.gov.hmcts.reform.preapi.entities.listeners.RecordingListener;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "recordings")
@EntityListeners(RecordingListener.class)
public class Recording extends BaseEntity implements ISoftDeletable {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "capture_session_id", referencedColumnName = "id")
    private CaptureSession captureSession;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_recording_id")
    private Recording parentRecording;

    @OneToMany(mappedBy = "parentRecording")
    private Set<Recording> recordings;

    @Column(name = "version", nullable = false)
    private int version;

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

    @Transient
    private boolean isSoftDeleteOperation;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("parentRecordingId", parentRecording != null ? parentRecording.getId() : null);
        details.put("recordingVersion", version);
        details.put("recordingFilename", filename);
        if (duration != null) {
            details.put("recordingDuration", duration.toString());
        }
        details.put("recordingEditInstruction", editInstruction);
        details.put("deleted", isDeleted());
        return details;
    }

    @Override
    public void setDeleteOperation(boolean deleteOperation) {
        this.isSoftDeleteOperation = deleteOperation;
    }

    @Override
    public boolean isDeleteOperation() {
        return this.isSoftDeleteOperation;
    }
}
