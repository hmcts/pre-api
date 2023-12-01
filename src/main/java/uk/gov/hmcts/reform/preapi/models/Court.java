package uk.gov.hmcts.reform.preapi.models;


import lombok.Value;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Value
public class Court {
    UUID id;
    CourtType courtType;
    String name;
    String locationCode;
    List<Region> regions;

    public Court(uk.gov.hmcts.reform.preapi.entities.Court court) {
        id = court.getId();
        courtType = court.getCourtType();
        name = court.getName();
        locationCode = court.getLocationCode();
        regions = court.getRegions().stream().map(Region::new).collect(Collectors.toList());
    }
}
