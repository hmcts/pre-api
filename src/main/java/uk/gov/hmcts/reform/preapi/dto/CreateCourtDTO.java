package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
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
    private UUID id;

    @Schema(description = "CreateCourtName")
    private String name;

    @Schema(description = "CreateCourtType")
    private CourtType courtType;

    @Schema(description = "CreateCourtLocationCode")
    private String locationCode;

    @Schema(description = "CreateCourtRegionIds")
    private List<UUID> regions = List.of();

    @Schema(description = "CreateCourtRoomIds")
    private List<UUID> rooms = List.of();
}
