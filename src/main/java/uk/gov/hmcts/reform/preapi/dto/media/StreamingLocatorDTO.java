package uk.gov.hmcts.reform.preapi.dto.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocator;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamingLocatorDTO {
    private String name;
    private String assetName;
    private String streamingLocatorId;
    private String streamingPolicyName;
    private Date created;
    private Date endTime;
    private StreamingLocatorPropertiesDTO properties;

    public StreamingLocatorDTO(MkStreamingLocator mkStreamingLocator) {
        this.name = mkStreamingLocator.getName();
        this.assetName = mkStreamingLocator.getAssetName();
        this.streamingLocatorId = mkStreamingLocator.getStreamingLocatorId();
        this.streamingPolicyName = mkStreamingLocator.getStreamingPolicyName();
        this.created = mkStreamingLocator.getCreated();
        this.endTime = mkStreamingLocator.getEndTime();

        this.properties = new StreamingLocatorPropertiesDTO(mkStreamingLocator.getProperties());
    }
}
