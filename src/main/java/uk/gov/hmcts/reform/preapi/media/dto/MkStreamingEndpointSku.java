package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
public class MkStreamingEndpointSku {
    private Tier name;
}

