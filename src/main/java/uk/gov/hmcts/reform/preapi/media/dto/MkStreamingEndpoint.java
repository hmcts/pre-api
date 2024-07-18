package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkStreamingEndpoint {
    private String location;
    private Map<String, String> tags;
    private MkStreamingEndpointProperties properties;
}
