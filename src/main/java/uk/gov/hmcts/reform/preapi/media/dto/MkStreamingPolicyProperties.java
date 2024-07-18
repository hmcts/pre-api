package uk.gov.hmcts.reform.preapi.media.dto;

import com.azure.resourcemanager.mediaservices.models.EnvelopeEncryption;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkStreamingPolicyProperties {
    private EnvelopeEncryption envelopeEncryption;
    private String defaultContentKeyPolicyName;
}
