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

    @Override
    public HashMap<String, Object> getDetailsForAudit() {
        var details = new HashMap<String, Object>();
        details.put("id", getId());
        details.put("sourceRecordingId", sourceRecording.getId());
        details.put("status", status);
        details.put("editInstruction", editInstruction);
        details.put("startedAt", startedAt);
        details.put("finishedAt", finishedAt);
        details.put("createdBy", createdBy.getId());
        return details;
    }
}
