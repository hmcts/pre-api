package uk.gov.hmcts.reform.preapi.models;


import lombok.Value;
import uk.gov.hmcts.reform.preapi.entities.Region;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for {@link Region}.
 */
@Value
public class RegionDto implements Serializable {
    UUID id;
    String name;

    public RegionDto(Region region) {
        id = region.getId();
        name = region.getName();
    }

}
