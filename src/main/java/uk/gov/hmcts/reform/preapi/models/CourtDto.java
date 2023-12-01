package uk.gov.hmcts.reform.preapi.models;


import lombok.Value;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DTO for {@link Court}.
 */
@Value
public class CourtDto implements Serializable {
    UUID id;
    CourtType courtType;
    String name;
    String locationCode;
    List<RegionDto> regions;

    public CourtDto(Court court) {
        id = court.getId();
        courtType = court.getCourtType();
        name = court.getName();
        locationCode = court.getLocationCode();
        regions = court.getRegions().stream().map(RegionDto::new).collect(Collectors.toList());
    }
}
