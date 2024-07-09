package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkStreamingLocatorUrlPaths {
    private List<String> downloadPaths;
    private MkStreamingLocatorDrm drm;
    private List<MkStreamingLocatorStreamingPath> streamingPaths;

    @Data
    @Builder
    public static class MkStreamingLocatorDrm {
        private String certificateUrl;
        private String licenseAcquisitionUrl;
    }

    @Data
    @Builder
    public static class MkStreamingLocatorStreamingPath {
        private EncryptionScheme encryptionScheme;
        private List<String> paths;
        private StreamingProtocol streamingProtocol;

        public enum EncryptionScheme {
            NoEncryption,
            EnvelopeEncryption,
            CommonEncryptionCenc,
            CommonEncryptionCbcs
        }

        public enum StreamingProtocol {
            Hls,
            Dash,
            Download,
            SmoothStreaming
        }
    }
}
