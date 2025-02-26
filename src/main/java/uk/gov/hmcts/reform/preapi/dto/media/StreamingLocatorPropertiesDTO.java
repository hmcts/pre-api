package uk.gov.hmcts.reform.preapi.dto.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorProperties;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamingLocatorPropertiesDTO {
    private String alternativeMediaId;
    private String assetName;
    private List<ContentKey> contentKeys;
    private String defaultContentKeyPolicyName;
    private Timestamp endTime;
    private List<String> filters;
    private Timestamp startTime;
    private String streamingLocatorId;
    private String streamingPolicyName;

    @Data
    @Builder
    public static class ContentKey {
        private String id;
        private String labelReferenceInStreamingPolicy;
        private String policyName;
        private String type;
        private String value;

        public ContentKey(String id, String labelReferenceInStreamingPolicy,
                          String policyName, String type, String value) {
            this.id = id;
            this.labelReferenceInStreamingPolicy = labelReferenceInStreamingPolicy;
            this.policyName = policyName;
            this.type = type;
            this.value = value;
        }
    }

    public StreamingLocatorPropertiesDTO(MkStreamingLocatorProperties mkStreamingLocatorProperties) {
        this.alternativeMediaId = mkStreamingLocatorProperties.getAlternativeMediaId();
        this.assetName = mkStreamingLocatorProperties.getAssetName();

        this.contentKeys = mkStreamingLocatorProperties.getContentKeys().stream()
                .map(mkContentKey -> new ContentKey(
                        mkContentKey.getId(),
                        mkContentKey.getLabelReferenceInStreamingPolicy(),
                        mkContentKey.getPolicyName(),
                        mkContentKey.getType(),
                        mkContentKey.getValue()
                ))
                .collect(Collectors.toList());

        this.defaultContentKeyPolicyName = mkStreamingLocatorProperties.getDefaultContentKeyPolicyName();
        this.endTime = mkStreamingLocatorProperties.getEndTime();
        this.filters = mkStreamingLocatorProperties.getFilters();
        this.startTime = mkStreamingLocatorProperties.getStartTime();
        this.streamingLocatorId = mkStreamingLocatorProperties.getStreamingLocatorId();
        this.streamingPolicyName = mkStreamingLocatorProperties.getStreamingPolicyName();
    }

}
