package uk.gov.hmcts.reform.preapi.media.dto;

import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkLiveEventProperties {
    private String created;
    private CrossSiteAccessPolicies crossSiteAccessPolicies;
    private String description;
    private Encoding encoding;
    private String hostnamePrefix;
    private LiveEventInput input;
    private String lastModified;
    private LiveEventPreview preview;
    private String provisioningState;
    private String resourceState;
    private List<String> streamOptions;
    private boolean useStaticHostname;

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CrossSiteAccessPolicies {
        private String clientAccessPolicy;
        private String crossDomainPolicy;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Encoding {
        private String encodingType; // None PassthroughBasic PassthroughStandard Premium1080p Standard
        private String keyFrameInterval;
        private String presetName;
        private String stretchMode; // None AutoSize AutoFit
    }
}