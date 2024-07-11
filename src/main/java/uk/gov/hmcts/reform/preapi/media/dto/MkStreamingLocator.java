package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkStreamingLocator {
    private String name;
    private String assetName;
    private String streamingLocatorId;
    private String streamingPolicyName;
    private Date created;
    private Date endTime;
    private MkStreamingLocatorProperties properties;
}
