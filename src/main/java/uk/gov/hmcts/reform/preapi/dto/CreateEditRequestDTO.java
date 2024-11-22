package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.validators.CreateEditRequestStatusConstraint;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@CreateEditRequestStatusConstraint
@Schema(description = "CreateEditRequestDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateEditRequestDTO {
    @NotNull
    @Schema(description = "CreateEditRequestId")
    private UUID id;

    @NotNull
    @Schema(description = "CreateEditRequestSourceRecordingId")
    private UUID sourceRecordingId;

    @NotNull
    @Schema(description = "CreateEditRequestStatus")
    private EditRequestStatus status;

    @Valid
    @NotEmpty
    @Schema(description = "CreateEditRequestInstructions")
    private List<EditCutInstructionDTO> editInstructions;

    @Schema(description = "CreateEditRequestJointlyAgreed")
    private Boolean jointlyAgreed;

    @Size(max = 512)
    @Schema(description = "CreateEditRequestRejectionReason")
    private String rejectionReason;

    @Schema(description = "CreateEditRequestApprovedAt")
    private Timestamp approvedAt;

    @Size(max = 100)
    @Schema(description = "CreateEditRequestApprovedBy")
    private String approvedBy;
}
