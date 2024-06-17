package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(fluent = true)
public class MkStreamingEndpoint {
    private String location;
    private Map<String, String> tags;
    private MkStreamingEndpointProperties properties;
}
