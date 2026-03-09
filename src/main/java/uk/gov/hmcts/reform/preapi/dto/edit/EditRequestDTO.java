package uk.gov.hmcts.reform.preapi.dto.edit;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EditRequestDTO {

    @NotNull
    @Schema(description = "EditRequestId")
    private UUID id;

    @NotNull
    @Schema(description = "EditRequestSourceRecording")
    private UUID sourceRecordingId;

    @Schema(description = "EditRequestOutputRecording")
    private UUID outputRecordingId;

    @NotNull
    @Schema(description = "EditRequestEditInstruction")
    private List<EditCutInstructionsDTO> editInstructions;

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

    @Size(max = 512)
    @Schema(description = "EditRequestRejectionReason")
    private String rejectionReason;

    @Schema(description = "EditRequestApprovedAt")
    private Timestamp approvedAt;

    @Size(max = 100)
    @Schema(description = "EditRequestApprovedBy")
    private String approvedBy;

    public EditRequestDTO(EditRequest editRequest) {
        this.id = editRequest.getId();
        this.sourceRecordingId = editRequest.getSourceRecordingId();
        this.outputRecordingId = editRequest.getOutputRecordingId();
        this.editInstructions = editInstructionsFromDTO(editRequest.getEditCutInstructions());
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

    public static List<EditCutInstructionsDTO> editInstructionsFromDTO(List<EditCutInstructions> editInstructions) {
        if (editInstructions == null) {
            return null;
        }
        return editInstructions.stream().map(EditCutInstructionsDTO::new).collect(Collectors.toList());
    }
}
