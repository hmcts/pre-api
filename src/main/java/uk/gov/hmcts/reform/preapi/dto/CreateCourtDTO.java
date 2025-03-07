package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Schema(description = "CreateCourtCounty")
    private String county;

    @Schema(description = "CreateCourtPostcode")
    @Pattern(regexp = "^[A-Z]{1,2}[0-9][0-9A-Z]? [0-9][A-Z]{2}$", message = "invalid postcode")
    private String postcode;

    @Schema(description = "CreateCourtRegionIds")
    @Size(min = 1, message = "must contain at least 1")
    @NotNull(message = "regions is required and must contain at least 1")
    private List<UUID> regions = List.of();

    @Schema(description = "CreateCourtRoomIds")
    @Size(min = 1, message = "must contain at least 1")
    @NotNull(message = "rooms is required and must contain at least 1")
    private List<UUID> rooms = List.of();
}
