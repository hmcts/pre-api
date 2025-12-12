package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;

import java.sql.Timestamp;
import java.util.UUID;

import static uk.gov.hmcts.reform.preapi.media.edit.EditInstructions.fromJson;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EditRequestDTO {
    @Schema(description = "EditRequestId")
    private UUID id;

    @Schema(description = "EditRequestSourceRecording")
    private RecordingDTO sourceRecording;

    @Schema(description = "EditRequestEditInstruction")
    private EditInstructions editInstruction;

    @Schema(description = "EditRequestStatus")
    private EditRequestStatus status;

    @Schema(description = "EditRequestStartedAt")
    private Timestamp startedAt;

    @Schema(description = "EditRequestFinishedAt")
    private Timestamp finishedAt;

    @Schema(description = "EditRequestCreatedById")
    private UUID createdById;

    @Schema(description = "EditRequestCreatedByName")
    private String createdBy;

    @Schema(description = "EditRequestCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "EditRequestModifiedAt")
    private Timestamp modifiedAt;

    @Schema(description = "EditRequestJointlyAgreed")
    private Boolean jointlyAgreed;

    @Schema(description = "EditRequestRejectionReason")
    private String rejectionReason;

    @Schema(description = "EditRequestApprovedAt")
    private Timestamp approvedAt;

    @Schema(description = "EditRequestApprovedBy")
    private String approvedBy;

    public EditRequestDTO(EditRequest editRequest) {
        this.id = editRequest.getId();
        this.sourceRecording = new RecordingDTO(editRequest.getSourceRecording());
        this.editInstruction = fromJson(editRequest.getEditInstruction());
        this.status = editRequest.getStatus();
        this.startedAt = editRequest.getStartedAt();
        this.finishedAt = editRequest.getFinishedAt();
        this.createdById = editRequest.getCreatedBy().getId();
        this.createdBy = editRequest.getCreatedBy().getFullName();
        this.createdAt = editRequest.getCreatedAt();
        this.modifiedAt = editRequest.getModifiedAt();
        this.jointlyAgreed = editRequest.getJointlyAgreed();
        this.rejectionReason = editRequest.getRejectionReason();
        this.approvedAt = editRequest.getApprovedAt();
        this.approvedBy = editRequest.getApprovedBy();
    }

    public EditRequestDTO(EditRequest editRequest, boolean includeSourceRecording) {
        this.id = editRequest.getId();
        if (includeSourceRecording) {
            this.sourceRecording = new RecordingDTO(editRequest.getSourceRecording());
        }
        this.editInstruction = fromJson(editRequest.getEditInstruction());
        this.status = editRequest.getStatus();
        this.startedAt = editRequest.getStartedAt();
        this.finishedAt = editRequest.getFinishedAt();
        this.createdById = editRequest.getCreatedBy().getId();
        this.createdAt = editRequest.getCreatedAt();
        this.createdBy = editRequest.getCreatedBy().getFullName();
        this.modifiedAt = editRequest.getModifiedAt();
        this.jointlyAgreed = editRequest.getJointlyAgreed();
        this.rejectionReason = editRequest.getRejectionReason();
        this.approvedAt = editRequest.getApprovedAt();
        this.approvedBy = editRequest.getApprovedBy();
    }
}
