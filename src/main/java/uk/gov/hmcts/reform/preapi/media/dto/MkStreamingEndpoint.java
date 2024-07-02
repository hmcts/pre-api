package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class MkStreamingEndpoint {
    private String name;
    private String location;
    private Map<String, String> tags;
    private MkStreamingEndpointProperties properties;


    @Data
    @Builder
    public static class MkStreamingEndpointProperties {
        private String description;
        private String hostName;
        private String resourceState;
    }
}
