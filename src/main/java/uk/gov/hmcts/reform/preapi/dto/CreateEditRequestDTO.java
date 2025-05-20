package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @Min(1)
    @NotNull
    @Schema(description = "CreateEditRequestInstructions")
    private List<EditCutInstructionDTO> editInstructions;
}
