package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.entities.base.ISoftDeletable;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "capture_sessions")
public class CaptureSession extends BaseEntity implements ISoftDeletable {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_id", referencedColumnName = "id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, columnDefinition = "recording_origin")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private RecordingOrigin origin;

    @Column(name = "ingest_address")
    private String ingestAddress;

    @Column(name = "live_output_url", length = 100)
    private String liveOutputUrl;

    @Column(name = "started_at")
    private Timestamp startedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "started_by_user_id", referencedColumnName = "id")
    private User startedByUser;

    @Column(name = "finished_at")
    private Timestamp finishedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "finished_by_user_id", referencedColumnName = "id")
    private User finishedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "recording_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private RecordingStatus status;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @Transient
    private boolean deleted;

    @Transient
    private boolean isSoftDeleteOperation;

    @OneToMany(mappedBy = "captureSession")
    @Fetch(FetchMode.SUBSELECT)
    private Set<Recording> recordings;

    @OneToMany(mappedBy = "captureSession")
    private Set<EncodeJob> encodeJobs;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        var details = new HashMap<String, Object>();
        details.put("bookingId", booking.getId());
        details.put("captureSessionOrigin", origin);
        details.put("captureSessionStartedAt", startedAt);
        if (startedByUser != null) {
            details.put("captureSessionStartedByUser", startedByUser.getEmail());
        }
        details.put("captureSessionFinishedAt", finishedAt);
        if (finishedByUser != null) {
            details.put("captureSessionFinishedByUser", finishedByUser.getEmail());
        }
        details.put("captureSessionStatus", status);
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
