package uk.gov.hmcts.reform.preapi.media.dto;

import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class MkLiveEvent {
    private String id;
    private String location;
    private String name;
    private Map<String, String> tags;
    private MkLiveEventProperties properties;


    @Data
    @Builder
    public static class MkLiveEventProperties {
        private String description;
        private boolean useStaticHostname;
        private LiveEventInput input;
        private String resourceState;
        private LiveEventPreview preview;
    }
}
