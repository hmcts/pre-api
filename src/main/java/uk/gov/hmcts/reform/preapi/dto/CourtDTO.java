package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "CourtDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CourtDTO {
    @Schema(description = "CourtId")
    private UUID id;

    @Schema(description = "CourtName")
    private String name;

    @Schema(description = "CourtType")
    private CourtType courtType;

    @Schema(description = "CourtLocationCode")
    private String locationCode;

    @Schema(description = "CourtCounty")
    private String county;

    @Schema(description = "CourtPostcode")
    private String postcode;

    @Schema(description = "CourtGroupEmail")
    private String groupEmail;

    @Schema(description = "CourtRegions")
    private List<RegionDTO> regions; // this was removed??

    public CourtDTO(Court courtEntity) {
        this.id = courtEntity.getId();
        this.name = courtEntity.getName();
        this.courtType = courtEntity.getCourtType();
        this.locationCode = courtEntity.getLocationCode();
        this.county = courtEntity.getCounty();
        this.postcode = courtEntity.getPostcode();
        this.regions = Stream.ofNullable(courtEntity.getRegions())
            .flatMap(regions -> regions.stream().map(RegionDTO::new))
            .sorted(Comparator.comparing(RegionDTO::getName))
            .collect(Collectors.toList());
    }
}
