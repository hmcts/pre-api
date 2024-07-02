package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class MkStreamingLocator {
    private String name;
    private String assetName;
    private String streamingLocatorId;
    private String streamingPolicyName;
    private Date created;
    private Date endTime;
    private MkStreamingLocatorProperties properties;

    @Data
    @Builder
    public static class MkStreamingLocatorProperties {
        private String assetName;
        private String streamingLocatorId;
        private String streamingPolicyName;
    }
}
