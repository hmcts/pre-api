package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkStreamingEndpointProperties {
    private boolean cdnEnabled;
    private String cdnProfile;
    private CdnProviderEnum cdnProvider;
    private CrossSiteAccessPolicies crossSiteAccessPolicies;
    private List<String> customHostNames;
    private String description;
    private ProvisioningState provisioningState;
    private ResourceState resourceState;
    private long scaleUnits;
    private MkStreamingEndpointSku sku;
    private String hostName;
    private String advancedSettingsName;

    @SuppressWarnings("PMD.UnusedPrivateField")
    public enum CdnProviderEnum {
        EMPTY(""),
        StandardAkamai("StandardAkamai");

        private final String value;

        CdnProviderEnum(String value) {
            this.value = value;
        }
    }

    public enum ProvisioningState {
        Succeeded,
        Failed,
        Canceled,
        InProgress,
        Deleting,
        Accepted
    }

    public enum ResourceState {
        Stopped,
        Starting,
        Running,
        Stopping,
        Creating
    }

    @Data
    @Builder
    public static class CrossSiteAccessPolicies {
        private String clientAccessPolicy;
        private String crossDomainPolicy;
    }
}
