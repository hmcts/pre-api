package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
@Schema(description = "UpdateBookingCaseDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UpdateBookingCaseDTO {
    @Schema(description = "UpdateBookingId")
    @NotNull(message = "id is required")
    private UUID bookingId;

    @Schema(description = "CaseReferenceId")
    @NotNull(message = "case_id is required")
    private UUID caseId;
}
