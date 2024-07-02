package uk.gov.hmcts.reform.preapi.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MkStreamingLocatorPaths {
    private List<MkStreamingPath> streamingPaths;

    @Data
    @Builder
    public static class MkStreamingPath {
        private String encryptionScheme;
        private List<String> paths;
        private String streamingProtocol;
    }
}
