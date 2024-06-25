package uk.gov.hmcts.reform.preapi.media.dto;

import com.azure.resourcemanager.mediaservices.models.Hls;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MkLiveOutput {
    private String name;
    private MkLiveOutputProperties properties;

    @Data
    @Builder
    public static class MkLiveOutputProperties {
        private String archiveWindowLength;
        private String assetName;
        private String description;
        private String manifestName;
        private Hls hls;
    }
}
