package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MkCreateStreamingLocator {
    private MkCreateStreamingLocatorProperties properties;

    @Data
    @Builder
    public static class MkCreateStreamingLocatorProperties {
        private String assetName;
        private String streamingPolicyName;
        private String endTime;
    }
}
