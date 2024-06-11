package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MkStreamingLocator {
    private String name;
    private MkStreamingLocatorProperties properties;

    @Data
    @Builder
    public static class MkStreamingLocatorProperties {
        private String assetName;
        private String streamingLocatorId;
        private String streamingPolicyName;
    }
}
