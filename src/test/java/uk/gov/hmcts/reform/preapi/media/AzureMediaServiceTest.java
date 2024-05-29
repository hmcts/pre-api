package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AssetsClient;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.LiveEventsClient;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureMediaService.class)
public class AzureMediaServiceTest {
    @MockBean
    private AzureMediaServices amsClient;

    private final String resourceGroup = "test-resource-group";
    private final String accountName = "test-account-name";

    private AzureMediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new AzureMediaService(resourceGroup, accountName, amsClient);
    }

    @DisplayName("Should get a valid asset and return an AssetDTO")
    @Test
    void getAssetSuccess() {
        var name = "test-asset-name";
        var mockAssetsClient = mock(AssetsClient.class);
        var asset = mock(AssetInner.class);
        when(asset.name()).thenReturn(name);
        when(asset.description()).thenReturn("description");
        when(asset.container()).thenReturn("container");
        when(asset.storageAccountName()).thenReturn("storage-account-name");

        when(amsClient.getAssets()).thenReturn(mockAssetsClient);
        when(amsClient.getAssets().get(resourceGroup, accountName, name)).thenReturn(asset);

        var model = mediaService.getAsset(name);
        assertThat(model).isNotNull();
        assertThat(model.getName()).isEqualTo(name);
        assertThat(model.getDescription()).isEqualTo("description");
        assertThat(model.getContainer()).isEqualTo("container");
        assertThat(model.getStorageAccountName()).isEqualTo("storage-account-name");
    }

    @DisplayName("Should throw 404 error when azure returns a 404 error")
    @Test
    void getAssetNotFound() {
        var name = "test-asset-name";
        var httpResponse = mock(HttpResponse.class);
        var mockAssetsClient = mock(AssetsClient.class);
        when(httpResponse.getStatusCode()).thenReturn(404);

        when(amsClient.getAssets()).thenReturn(mockAssetsClient);
        when(amsClient.getAssets().get(resourceGroup, accountName, name))
            .thenThrow(new ManagementException("not found", httpResponse));

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.getAsset(name)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Asset with name: " + name);
    }

    @DisplayName("Should throw any other management exception when not 404 response from Azure")
    @Test
    void getAssetManagementException() {
        var name = "test-asset-name";
        var httpResponse = mock(HttpResponse.class);
        var mockAssetsClient = mock(AssetsClient.class);
        when(httpResponse.getStatusCode()).thenReturn(400);

        when(amsClient.getAssets()).thenReturn(mockAssetsClient);
        when(amsClient.getAssets().get(resourceGroup, accountName, name))
            .thenThrow(new ManagementException("bad request", httpResponse));

        var message = assertThrows(
            ManagementException.class,
            () -> mediaService.getAsset(name)
        ).getMessage();

        assertThat(message).isEqualTo("bad request");
    }

    @DisplayName("Should get a list of all assets from Azure")
    @Test
    void getAssetsListSuccess() {
        var asset = mock(AssetInner.class);
        when(asset.name()).thenReturn("name");
        when(asset.description()).thenReturn("description");
        when(asset.container()).thenReturn("container");
        when(asset.storageAccountName()).thenReturn("storage-account-name");

        var mockedClient = mock(AssetsClient.class);
        when(amsClient.getAssets()).thenReturn(mockedClient);
        when(mockedClient.list(resourceGroup, accountName)).thenReturn(mock());
        when(mockedClient.list(resourceGroup, accountName).stream()).thenReturn(Stream.of(asset));

        var models = mediaService.getAssets();
        assertThat(models).isNotNull();
        assertThat(models.size()).isEqualTo(1);
        assertThat(models.getFirst().getName()).isEqualTo("name");
        assertThat(models.getFirst().getDescription()).isEqualTo("description");
        assertThat(models.getFirst().getContainer()).isEqualTo("container");
        assertThat(models.getFirst().getStorageAccountName()).isEqualTo("storage-account-name");
    }

    @DisplayName("Should return a valid live event by name")
    @Test
    void getLiveEventByNameSuccess() {
        var name = "test-live-event-name";
        var mockLiveEventClient = mock(LiveEventsClient.class);
        var liveEvent = mock(LiveEventInner.class);
        when(liveEvent.id()).thenReturn("id");
        when(liveEvent.name()).thenReturn(name);
        when(liveEvent.description()).thenReturn("description");
        when(liveEvent.resourceState()).thenReturn(LiveEventResourceState.STOPPED);
        when(liveEvent.input())
            .thenReturn(new LiveEventInput()
                            .withEndpoints(List.of(new LiveEventEndpoint()
                                                       .withProtocol("RTMP")
                                                       .withUrl("rtmps://example"))));
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);
        when(amsClient.getLiveEvents().get(resourceGroup, accountName, name)).thenReturn(liveEvent);

        var model = mediaService.getLiveEvent(name);
        assertThat(model).isNotNull();
        assertThat(model.getId()).isEqualTo(liveEvent.id());
        assertThat(model.getName()).isEqualTo(name);
        assertThat(model.getDescription()).isEqualTo("description");
        assertThat(model.getResourceState()).isEqualTo("Stopped");
        assertThat(model.getInputRtmp()).isEqualTo("rtmps://example");
    }
}
