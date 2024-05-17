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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void getAssetsSuccess() {
        var asset1 = createMkAsset("asset1");
        var asset2 = createMkAsset("asset2");
        var mockAssets = MkGetListResponse.<MkAsset>builder()
            .value(List.of(asset1, asset2))
            .build();

        when(mockClient.getAssets(eq(0), anyString())).thenReturn(mockAssets);

        var assets = mediaKind.getAssets();

        assertThat(assets).hasSize(2);
        assertThat(assets.get(0).getName()).isEqualTo(asset1.getName());
        assertThat(assets.get(1).getName()).isEqualTo(asset2.getName());
    }

    @DisplayName("Should fail to get a list of assets and throw a media kind exception")
    @Test
    void getAssetsFeignExceptionThrown() {
        when(mockClient.getAssets(eq(0), anyString()))
            .thenThrow(FeignException.class);

        var message = assertThrows(
            MediaKindException.class,
            () -> mediaKind.getAssets()
        ).getMessage();

        assertThat(message).isEqualTo("Unable to connect to Media Service");
    }


    @Test
    void getAllPaginatedMkListEmpty() {
        MediaKind.GetListFunction<String> mockFunction = mock((skip ->
            MkGetListResponse.<String>builder().value(List.of()).build()));

        var result = mediaKind.getAllMkList(mockFunction);

        assertThat(result).hasSize(0);
    }

    @Test
    void getAllPaginatedMkListOnePage() {
        MediaKind.GetListFunction<String> mockFunction = mock((skip ->
            MkGetListResponse.<String>builder().value(List.of("item 1", "item 2")).build()));

        var result = mediaKind.getAllMkList(mockFunction);

        assertThat(result).hasSize(2);
        assertThat(result.contains("item 1")).isTrue();
        assertThat(result.contains("item 2")).isTrue();
    }

    @Test
    void getAllPaginatedMkListMultiPage() {
        MediaKind.GetListFunction<String> mockFunction = mock((skip ->
            MkGetListResponse.<String>builder().build())
        );

        when(mockFunction.get(0)).thenReturn(
            MkGetListResponse.<String>builder()
                .value(List.of("item 1", "item 2"))
                .nextLink("next link skip token = 2")
                .supplemental(MkGetListResponse.Supplemental.builder()
                                  .pagination(MkGetListResponse.MkPagination.builder()
                                                  .end(2).build())
                                  .build())
                .build()
        );
        when(mockFunction.get(2)).thenReturn(
            MkGetListResponse.<String>builder()
                .value(List.of("item 3", "item 4"))
                .build()
        );

        var result = mediaKind.getAllMkList(mockFunction);

        assertThat(result).hasSize(4);
        assertThat(result.contains("item 1")).isTrue();
        assertThat(result.contains("item 2")).isTrue();
        assertThat(result.contains("item 3")).isTrue();
        assertThat(result.contains("item 4")).isTrue();
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
