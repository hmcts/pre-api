package uk.gov.hmcts.reform.preapi.entities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jetbrains.annotations.NotNull;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "edit_requests")
public class EditRequest extends CreatedModifiedAtEntity {

    @NotNull
    @OneToOne
    @JoinTable(
        name = "edit_cut_instructions",
        joinColumns = @JoinColumn(name = "edit_request_id", referencedColumnName = "id", nullable = false))
    private EditCutInstructions editCutInstructions;

    public void setEditCutInstructions(@NotNull String editCutInstructions) {
        try {
            this.editCutInstructions = new ObjectMapper().readValue(editCutInstructions, EditCutInstructions.class);
        } catch (JsonProcessingException e) {
            throw new UnknownServerException("Unable to read edit instructions", e);
        }
    }

    @NotNull
    @Column(name = "source_recording_id")
    private UUID sourceRecordingId;

    @Column(name = "output_recording_id")
    private UUID outputRecordingId;

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
        details.put("sourceRecordingId", sourceRecordingId);
        details.put("status", status);
        details.put("editInstructions", editCutInstructions);
        details.put("startedAt", startedAt);
        details.put("finishedAt", finishedAt);
        details.put("createdBy", createdBy.getId());
        details.put("jointlyAgreed", jointlyAgreed);
        details.put("rejectionReason", rejectionReason);
        details.put("approvedAt", approvedAt);
        details.put("approvedBy", approvedBy);
        return details;
    }

    public static EditRequest fromJson(String jsonString) {
        try {
            return new ObjectMapper().readValue(jsonString, EditRequest.class);
        } catch (Exception e) {
            throw new UnknownServerException("Unable to read edit request", e);
        }
    }

    public static EditRequest tryFromJson(String jsonString) {
        try {
            return new ObjectMapper().readValue(jsonString, EditRequest.class);
        } catch (Exception e) {
            return null;
        }
    }
}
