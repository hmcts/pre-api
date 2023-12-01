package uk.gov.hmcts.reform.preapi.models;


import lombok.Value;

import java.util.UUID;

@Value
public class Region {
    UUID id;
    String name;

    public Region(uk.gov.hmcts.reform.preapi.entities.Region region) {
        id = region.getId();
        name = region.getName();
    }
}
