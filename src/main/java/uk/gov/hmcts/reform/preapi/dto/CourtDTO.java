package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CourtDTO {
    private UUID id;
    private String name;
    private CourtType courtType;
    private String locationCode;
    private Set<RegionDTO> regions; // this was removed??
    // Set<Room> room;

    public CourtDTO(Court courtEntity) {
        this.id = courtEntity.getId();
        this.name = courtEntity.getName();
        this.courtType = courtEntity.getCourtType();
        this.locationCode = courtEntity.getLocationCode();
        this.regions = Stream.ofNullable(courtEntity.getRegions())
            .flatMap(regions -> regions.stream().map(RegionDTO::new))
            .collect(Collectors.toSet());
    }
}
