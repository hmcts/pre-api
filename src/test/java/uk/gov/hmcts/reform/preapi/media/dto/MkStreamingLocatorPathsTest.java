package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.config.JacksonConfiguration;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.EncryptionScheme;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.StreamingProtocol;

import static org.assertj.core.api.Assertions.assertThat;

public class MkStreamingLocatorPathsTest {

    private static String jsonSnippet = """
          {
          "streamingPaths": [
            {
              "streamingProtocol": "Dash",
              "encryptionScheme": "NoEncryption",
              "paths": [
                "/78efb6a93bed405e8eadeb6d1a95c9f3/index.qfm/manifest(format=mpd-time-cmaf)"
              ]
            },
            {
              "streamingProtocol": "Hls",
              "encryptionScheme": "NoEncryption",
              "paths": [
                "/78efb6a93bed405e8eadeb6d1a95c9f3/index.qfm/manifest(format=m3u8-cmaf)"
              ]
            }
          ],
          "downloadPaths": [],
          "drm": {}
        }
        """;

    @DisplayName("Should parse example json string to MkStreamingLocatorUrlPaths object")
    @Test
    void getLiveEventByNameSuccess() throws JsonProcessingException {
        var om = new JacksonConfiguration().getMapper();
        var paths = om.readValue(jsonSnippet, MkStreamingLocatorUrlPaths.class);

        assertThat(paths.getStreamingPaths().size()).isEqualTo(2);

        var result = paths.getStreamingPaths().stream()
             .filter(p -> p.getEncryptionScheme() == EncryptionScheme.NoEncryption
                 && p.getStreamingProtocol() == StreamingProtocol.Hls)
             .flatMap(path -> path.getPaths().stream())
             .findFirst()
             .map(p -> "https://" + p)
             .orElseThrow(() -> new RuntimeException("No valid paths returned from Streaming Locator"));

        assertThat(result).isEqualTo("https:///78efb6a93bed405e8eadeb6d1a95c9f3/index.qfm/manifest(format=m3u8-cmaf)");
    }
}
