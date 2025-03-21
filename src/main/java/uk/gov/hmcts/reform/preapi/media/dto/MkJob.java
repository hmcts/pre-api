package uk.gov.hmcts.reform.preapi.media.dto;

import com.azure.resourcemanager.mediaservices.models.JobInputClip;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MkJob {
    private String name;
    private MkJobProperties properties;

    @Data
    @Builder
    public static class MkJobProperties {
        private String description;
        private JobInputClip input;
        private List<JobOutputAsset> outputs;
        private JobState state;
    }
}

