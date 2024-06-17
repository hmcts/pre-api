package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@Accessors(fluent = true)
public class MkStreamingLocatorProperties {
    private String alternativeMediaId;
    private String assetName;
    private List<MkContentKey> contentKeys;
    private String defaultContentKeyPolicyName;
    private LocalDateTime endTime;
    private List<String> filters;
    private LocalDateTime startTime;
    private String streamingLocatorId;
    private String streamingPolicyName;

    @Data
    @Builder
    public static class MkContentKey {
        private String id;
        private String labelReferenceInStreamingPolicy;
        private String policyName;
        private String type;
        private String value;
    }

}
