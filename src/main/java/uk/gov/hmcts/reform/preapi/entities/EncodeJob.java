package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "encode_jobs")
public class EncodeJob extends CreatedModifiedAtEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "capture_session_id", referencedColumnName = "id")
    private CaptureSession captureSession;

    @Column(name = "recording_id", nullable = false)
    private UUID recordingId;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(name = "transform", nullable = false, columnDefinition = "encode_transform")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EncodeTransform transform;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        var details = new HashMap<String, Object>();
        details.put("captureSessionId", captureSession.getId());
        details.put("recordingId", recordingId);
        details.put("jobName", jobName);
        details.put("transform", transform);
        return details;
    }
}
