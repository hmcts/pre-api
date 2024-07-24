package uk.gov.hmcts.reform.preapi.media.dto;

import com.azure.resourcemanager.mediaservices.models.ContentKeyPolicyClearKeyConfiguration;
import com.azure.resourcemanager.mediaservices.models.ContentKeyPolicyTokenRestriction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkContentKeyPolicyOptions {
    private String name;
    private ContentKeyPolicyClearKeyConfiguration configuration;
    private ContentKeyPolicyTokenRestriction restriction;
}
