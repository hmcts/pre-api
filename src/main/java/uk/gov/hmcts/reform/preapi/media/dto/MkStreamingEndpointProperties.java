package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class MkStreamingEndpointProperties {
    private AccessControl accessControl;
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

    public enum CdnProviderEnum {
        EMPTY(""),
        StandardAkamai("StandardAkamai");

        private final String value;

        CdnProviderEnum(String value) {
            this.value = value;
        }

        public static CdnProviderEnum fromValue(String value) {
            for (CdnProviderEnum enumValue : CdnProviderEnum.values()) {
                if (enumValue.value.equals(value)) {
                    return enumValue;
                }
            }
            throw new IllegalArgumentException("Unexpected value: " + value);
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
        Stopping
    }

    @Data
    @Builder
    public static class AccessControl {
        private String ip;
        private String akamai;
    }

    @Data
    @Builder
    public static class CrossSiteAccessPolicies {
        private String clientAccessPolicy;
        private String crossDomainPolicy;
    }
}