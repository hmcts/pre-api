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
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "edit_requests")
public class EditRequest extends CreatedModifiedAtEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_recording_id", referencedColumnName = "id", nullable = false)
    private Recording sourceRecording;

    @Column(name = "edit_instruction", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String editInstruction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "edit_request_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EditRequestStatus status;

    @Column(name = "started_at")
    private Timestamp startedAt;

    @Column(name = "finished_at")
    private Timestamp finishedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", referencedColumnName = "id", nullable = false)
    private User createdBy;

    @Column(name = "jointly_agreed")
    private Boolean jointlyAgreed;

    @Column(name = "rejection_reason", length = 512)
    private String rejectionReason;

    @Column(name = "approved_at")
    private Timestamp approvedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Override
    public Map<String, Object> getDetailsForAudit() {
        Map<String, Object> details = new HashMap<>();
        details.put("id", getId());
        details.put("sourceRecordingId", sourceRecording.getId());
        details.put("status", status);
        details.put("editInstruction", editInstruction);
        details.put("startedAt", startedAt);
        details.put("finishedAt", finishedAt);
        details.put("createdBy", createdBy.getId());
        details.put("jointlyAgreed", jointlyAgreed);
        details.put("rejectionReason", rejectionReason);
        details.put("approvedAt", approvedAt);
        details.put("approvedBy", approvedBy);
        return details;
    }
}
