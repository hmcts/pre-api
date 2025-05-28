package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateCourtDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateCourtDTO {
    @Schema(description = "CreateCourtId")
    @NotNull(message = "id is required")
    private UUID id;

    @Schema(description = "CreateCourtName")
    @NotNull(message = "name is required")
    private String name;

    @Schema(description = "CreateCourtType")
    @NotNull(message = "court_type is required")
    private CourtType courtType;

    @Schema(description = "CreateCourtLocationCode")
    @NotNull(message = "location_code is required")
    private String locationCode;

    @Schema(description = "CreateCourtRegionIds")
    @Size(min = 1, message = "must contain at least 1")
    @NotNull(message = "regions is required and must contain at least 1")
    private List<UUID> regions = List.of();
}
