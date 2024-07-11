package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkStreamingLocatorProperties {
    private String alternativeMediaId;
    private String assetName;
    private List<MkContentKey> contentKeys;
    private String defaultContentKeyPolicyName;
    private Timestamp endTime;
    private List<String> filters;
    private Timestamp startTime;
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
