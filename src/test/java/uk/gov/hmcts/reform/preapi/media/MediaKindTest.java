package uk.gov.hmcts.reform.preapi.media;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = MediaKind.class)
public class MediaKindTest {
    @Mock
    private MediaKindClient mockClient;

    private static MediaKind mediaKind;

    @BeforeEach
    void setUp() {
        mediaKind = new MediaKind("https://api.mk.io/api/ams/EXAMPLE/", "exampleToken");
        mediaKind.client = mockClient;
    }

    @DisplayName("Should get a list of assets")
    @Test
    void testGetAssets_Success() {
        var asset1 = createMkAsset("asset1");
        var asset2 = createMkAsset("asset2");
        var mockAssets = MkGetListResponse.<MkAsset>builder()
            .value(List.of(asset1, asset2))
            .build();

        when(mockClient.getAssets(anyString())).thenReturn(mockAssets);

        var assets = mediaKind.getAssets();

        assertThat(assets).hasSize(2);
        assertThat(assets.get(0).getName()).isEqualTo(asset1.getName());
        assertThat(assets.get(1).getName()).isEqualTo(asset2.getName());
    }

    @DisplayName("Should fail to get a list of assets and throw a media kind exception")
    @Test
    void testGetAssets_FeignExceptionThrown() {
        when(mockClient.getAssets(anyString()))
            .thenThrow(FeignException.class);

        var message = assertThrows(
            MediaKindException.class,
            () -> mediaKind.getAssets()
        ).getMessage();

        assertThat(message).isEqualTo("Unable to connect to Media Service");
    }

    @DisplayName("Should throw Unsupported Operation Exception when method is not defined")
    @Test
    void unsupportedOperationException() {
        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaKind.playAsset("test-asset-name")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaKind.importAsset("test-asset-name")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaKind.getAsset("test-asset-name")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaKind.startLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaKind.playLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaKind.stopLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaKind.getLiveEvent("live-event-id")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaKind.getLiveEvents()
        );
    }

    private MkAsset createMkAsset(String name) {
        return MkAsset.builder()
            .name(name)
            .properties(MkAssetProperties.builder()
                            .description("description: " + name)
                            .storageAccountName("example storage account")
                            .container("container name")
                            .build())
            .build();
    }
}
