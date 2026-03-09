package uk.gov.hmcts.reform.preapi.entities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jetbrains.annotations.NotNull;
import uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@Entity
@Table(name = "edit_requests")
public class EditRequest extends CreatedModifiedAtEntity {

    @NotNull
    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "edit_cut_instructions",
        joinColumns = @JoinColumn(name = "edit_request_id", referencedColumnName = "id", nullable = false))
    private List<EditCutInstructions> editCutInstructions;

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

    public void setEditCutInstructions(@NotNull List<EditCutInstructions> editCutInstructions) {
        this.editCutInstructions = editCutInstructions;
    }

    public String getEditCutInstructionsAsJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this.editCutInstructions);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public void setEditCutInstructionsFromJson(@NotNull String editCutInstructionsAsJson) {
        // Need to pick out requested instructions as that's the important bit

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<EditCutInstructions> editCutInstructions = new ArrayList<>();

        try {
            JsonNode jsonNode = objectMapper.readTree(editCutInstructionsAsJson);

            String editRequestIdAsString = jsonNode.get("editRequestId").asText();
            UUID editRequestId;

            try {
                editRequestId = UUID.fromString(editRequestIdAsString);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid edit request UUID: " + editRequestIdAsString);
            }

            JsonNode requestedInstructionsNode = jsonNode.path("editInstructions")
                .path("requestedInstructions");

            if (requestedInstructionsNode.isArray()) {
                for (JsonNode instruction : requestedInstructionsNode) {
                    Integer start = instruction.path("start").asInt();
                    Integer end = instruction.path("end").asInt();
                    String reason = instruction.path("reason").asText();
                    editCutInstructions.add(new EditCutInstructions(editRequestId, start, end, reason));
                }
            }

            Comparator<EditCutInstructions> customComparator = Comparator.comparing(EditCutInstructions::getStart);
            this.editCutInstructions = editCutInstructions.stream().sorted(customComparator).toList();
        } catch (JsonProcessingException e) {
            throw new UnknownServerException("Unable to read edit instructions", e);
        }
    }
}
