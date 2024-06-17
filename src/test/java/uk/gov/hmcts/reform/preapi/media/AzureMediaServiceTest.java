package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AssetsClient;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.LiveEventsClient;
import com.azure.resourcemanager.mediaservices.fluent.StreamingEndpointsClient;
import com.azure.resourcemanager.mediaservices.fluent.StreamingLocatorsClient;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.ListPathsResponseInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingEndpointInner;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import com.azure.resourcemanager.mediaservices.models.StreamingPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.exception.AMSLiveEventNotFoundException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        mediaService = new AzureMediaService(resourceGroup, accountName, "unittesting", amsClient);
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

    @DisplayName("Should throw not found error when AMS returns 404")
    @Test
    void getLiveEventByNameNotFound() {
        var name = "test-live-event-name";
        var httpResponse = mock(HttpResponse.class);
        var mockLiveEventClient = mock(LiveEventsClient.class);
        when(httpResponse.getStatusCode()).thenReturn(404);
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);
        when(amsClient.getLiveEvents().get(resourceGroup, accountName, name))
            .thenThrow(new ManagementException("not found", httpResponse));

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.getLiveEvent(name)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Live event: " + name);
    }

    @DisplayName("Should throw any other management exception when not 404 response from Azure (live event)")
    @Test
    void getLiveEventManagementException() {
        var name = "test-live-event-name";
        var httpResponse = mock(HttpResponse.class);
        var mockClient = mock(LiveEventsClient.class);
        when(httpResponse.getStatusCode()).thenReturn(400);

        when(amsClient.getLiveEvents()).thenReturn(mockClient);
        when(amsClient.getLiveEvents().get(resourceGroup, accountName, name))
            .thenThrow(new ManagementException("bad request", httpResponse));

        var message = assertThrows(
            ManagementException.class,
            () -> mediaService.getLiveEvent(name)
        ).getMessage();

        assertThat(message).isEqualTo("bad request");
    }

    @DisplayName("Should return a list of live events")
    @Test
    void getLiveEventsSuccess() {
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
        when(mockLiveEventClient.list(resourceGroup, accountName)).thenReturn(mock());
        when(amsClient.getLiveEvents().list(resourceGroup, accountName).stream()).thenReturn(Stream.of(liveEvent));

        var results = mediaService.getLiveEvents();
        assertThat(results).isNotNull();
        assertThat(results).hasSize(1);

        var model = results.getFirst();
        assertThat(model).isNotNull();
        assertThat(model.getId()).isEqualTo(liveEvent.id());
        assertThat(model.getName()).isEqualTo(name);
        assertThat(model.getDescription()).isEqualTo("description");
        assertThat(model.getResourceState()).isEqualTo("Stopped");
        assertThat(model.getInputRtmp()).isEqualTo("rtmps://example");
    }

    @DisplayName("Should complete play live event successfully, creating all required resources")
    @Test
    void playLiveEventFreshSuccess() {
        var mockLiveEventClient = mock(LiveEventsClient.class);
        var mockLiveEvent = mock(LiveEventInner.class);
        when(mockLiveEvent.resourceState()).thenReturn(LiveEventResourceState.RUNNING);
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);
        var liveEventId = UUID.fromString("c154d36e-cab4-4aaa-a4c7-11d89a27634f");
        var sanitisedLiveEventId = liveEventId.toString().replace("-", "");
        when(mockLiveEventClient.get(resourceGroup, accountName, sanitisedLiveEventId))
            .thenReturn(mockLiveEvent);

        var mockStreamingEndpointsClient = mock(StreamingEndpointsClient.class);
        var mockStreamingEndpointInner = mock(StreamingEndpointInner.class);
        when(mockStreamingEndpointInner.hostname()).thenReturn("pre-example.com");

        when(amsClient.getStreamingEndpoints()).thenReturn(mockStreamingEndpointsClient);
        var shortenedLiveEventId = sanitisedLiveEventId.substring(0, 23);

        when(mockStreamingEndpointsClient
                 .create(eq(resourceGroup),
                         eq(accountName),
                         eq("c154d36ecab44aaaa4c711d"),
                         any(StreamingEndpointInner.class))
        ).thenReturn(mockStreamingEndpointInner);

        var mockStreamingLocatorsClient = mock(StreamingLocatorsClient.class);
        when(amsClient.getStreamingLocators()).thenReturn(mockStreamingLocatorsClient);

        var mockListPathsResponseInner = mock(ListPathsResponseInner.class);
        var mockStreamingPath1 = mock(StreamingPath.class);
        when(mockStreamingPath1.paths()).thenReturn(List.of("/path1", "/path1.1"));
        var mockStreamingPath2 = mock(StreamingPath.class);
        when(mockStreamingPath2.paths()).thenReturn(List.of("/path2", "/path2.2"));
        when(mockListPathsResponseInner.streamingPaths()).thenReturn(List.of(mockStreamingPath1, mockStreamingPath2));
        when(mockStreamingLocatorsClient.listPaths(resourceGroup, accountName, sanitisedLiveEventId))
            .thenReturn(mockListPathsResponseInner);

        var response = mediaService.playLiveEvent(liveEventId);
        assertThat(response).isEqualTo("https://pre-example.com/path1");

        Mockito.verify(mockStreamingEndpointsClient, Mockito.times(1))
               .start(resourceGroup, accountName, shortenedLiveEventId);

        Mockito.verify(mockStreamingLocatorsClient, Mockito.times(1))
               .create(eq(resourceGroup),
                       eq(accountName),
                       eq(sanitisedLiveEventId),
                       any());
    }

    @DisplayName("Should complete play live event successfully, all required resources already exist")
    @Test
    void playLiveEventFreshSuccessPreExistingResources() {
        var mockLiveEventClient = mock(LiveEventsClient.class);
        var mockLiveEvent = mock(LiveEventInner.class);
        when(mockLiveEvent.resourceState()).thenReturn(LiveEventResourceState.RUNNING);
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);
        var liveEventId = UUID.fromString("c154d36e-cab4-4aaa-a4c7-11d89a27634f");
        var sanitisedLiveEventId = liveEventId.toString().replace("-", "");
        when(mockLiveEventClient.get(resourceGroup, accountName, sanitisedLiveEventId))
            .thenReturn(mockLiveEvent);

        var mockStreamingEndpointsClient = mock(StreamingEndpointsClient.class);
        var mockStreamingEndpointInner = mock(StreamingEndpointInner.class);
        when(mockStreamingEndpointInner.hostname()).thenReturn("pre-example.com");

        when(amsClient.getStreamingEndpoints()).thenReturn(mockStreamingEndpointsClient);
        var shortenedLiveEventId = sanitisedLiveEventId.substring(0, 23);

        var mockHttpResponse = mock(HttpResponse.class);
        when(mockHttpResponse.getStatusCode()).thenReturn(409);

        when(mockStreamingEndpointsClient
                 .create(eq(resourceGroup),
                         eq(accountName),
                         eq("c154d36ecab44aaaa4c711d"),
                         any(StreamingEndpointInner.class))
        ).thenThrow(new ManagementException("Already exists", mockHttpResponse));

        when(mockStreamingEndpointsClient.get(resourceGroup, accountName, shortenedLiveEventId))
            .thenReturn(mockStreamingEndpointInner);

        var mockStreamingLocatorsClient = mock(StreamingLocatorsClient.class);
        when(amsClient.getStreamingLocators()).thenReturn(mockStreamingLocatorsClient);
        when(mockStreamingLocatorsClient.create(eq(resourceGroup), eq(accountName), eq(sanitisedLiveEventId), any()))
            .thenThrow(new ManagementException("Already exists", mockHttpResponse));

        var mockListPathsResponseInner = mock(ListPathsResponseInner.class);
        var mockStreamingPath1 = mock(StreamingPath.class);
        when(mockStreamingPath1.paths()).thenReturn(List.of("/path1", "/path1.1"));
        var mockStreamingPath2 = mock(StreamingPath.class);
        when(mockStreamingPath2.paths()).thenReturn(List.of("/path2", "/path2.2"));
        when(mockListPathsResponseInner.streamingPaths()).thenReturn(List.of(mockStreamingPath1, mockStreamingPath2));
        when(mockStreamingLocatorsClient.listPaths(resourceGroup, accountName, sanitisedLiveEventId))
            .thenReturn(mockListPathsResponseInner);

        var response = mediaService.playLiveEvent(liveEventId);
        assertThat(response).isEqualTo("https://pre-example.com/path1");
    }

    @DisplayName("Should throw an exception when live event doesn't exist")
    @Test
    void playLiveEventAMSLiveEventNotFoundException() {
        var liveEventId = UUID.fromString("c154d36e-cab4-4aaa-a4c7-11d89a27634f");
        var sanitisedLiveEventId = liveEventId.toString().replace("-", "");

        var mockLiveEventClient = mock(LiveEventsClient.class);

        var mockHttpResponse = mock(HttpResponse.class);
        when(mockHttpResponse.getStatusCode()).thenReturn(404);

        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);

        when(mockLiveEventClient.get(resourceGroup, accountName, sanitisedLiveEventId))
            .thenThrow(new ManagementException("not found", mockHttpResponse));

        assertThrows(AMSLiveEventNotFoundException.class, () -> mediaService.playLiveEvent(liveEventId));
    }

    @DisplayName("Should throw an exception when live event exists but is not running")
    @Test
    void playLiveEventAMSLiveEventNotRunningException() {
        var liveEventId = UUID.fromString("c154d36e-cab4-4aaa-a4c7-11d89a27634f");
        var sanitisedLiveEventId = liveEventId.toString().replace("-", "");

        var mockLiveEventClient = mock(LiveEventsClient.class);
        var mockLiveEvent = mock(LiveEventInner.class);
        when(mockLiveEvent.resourceState()).thenReturn(LiveEventResourceState.STARTING);
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);

        when(mockLiveEventClient.get(resourceGroup, accountName, sanitisedLiveEventId))
            .thenReturn(mockLiveEvent);

        assertThrows(LiveEventNotRunningException.class, () -> mediaService.playLiveEvent(liveEventId));
    }

    @DisplayName("Should throw an exception when unable to check live event exists")
    @Test
    void playLiveEventAMSLiveEvent500() {
        var liveEventId = UUID.fromString("c154d36e-cab4-4aaa-a4c7-11d89a27634f");
        var sanitisedLiveEventId = liveEventId.toString().replace("-", "");

        var mockLiveEventClient = mock(LiveEventsClient.class);

        var mockHttpResponse = mock(HttpResponse.class);
        when(mockHttpResponse.getStatusCode()).thenReturn(500);

        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);

        when(mockLiveEventClient.get(resourceGroup, accountName, sanitisedLiveEventId))
            .thenThrow(new ManagementException("Internal Server Error", mockHttpResponse));

        assertThrows(ManagementException.class, () -> mediaService.playLiveEvent(liveEventId));
    }

    @DisplayName("Should throw a ManagementException unable to check if Streaming Locator Exists")
    @Test
    void playLiveEventAssertStreamingLocatorExistsError() {
        var mockLiveEventClient = mock(LiveEventsClient.class);
        var mockLiveEvent = mock(LiveEventInner.class);
        when(mockLiveEvent.resourceState()).thenReturn(LiveEventResourceState.RUNNING);
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);
        var liveEventId = UUID.fromString("c154d36e-cab4-4aaa-a4c7-11d89a27634f");
        var sanitisedLiveEventId = liveEventId.toString().replace("-", "");
        when(mockLiveEventClient.get(resourceGroup, accountName, sanitisedLiveEventId))
            .thenReturn(mockLiveEvent);

        var mockStreamingEndpointsClient = mock(StreamingEndpointsClient.class);
        var mockStreamingEndpointInner = mock(StreamingEndpointInner.class);
        when(mockStreamingEndpointInner.hostname()).thenReturn("pre-example.com");

        when(amsClient.getStreamingEndpoints()).thenReturn(mockStreamingEndpointsClient);

        when(mockStreamingEndpointsClient
                 .create(eq(resourceGroup),
                         eq(accountName),
                         eq("c154d36ecab44aaaa4c711d"),
                         any(StreamingEndpointInner.class))
        ).thenThrow(new ManagementException("bad request", mock(HttpResponse.class)));

        assertThrows(ManagementException.class, () -> mediaService.playLiveEvent(liveEventId));
    }

    @DisplayName("Should throw a RuntimeException if all paths are blank")
    @Test
    void playLiveEventParseLiveOutputUrlFromStreamingLocatorPathsRuntimeException() {
        var mockLiveEventClient = mock(LiveEventsClient.class);
        var mockLiveEvent = mock(LiveEventInner.class);
        when(mockLiveEvent.resourceState()).thenReturn(LiveEventResourceState.RUNNING);
        when(amsClient.getLiveEvents()).thenReturn(mockLiveEventClient);
        var liveEventId = UUID.fromString("c154d36e-cab4-4aaa-a4c7-11d89a27634f");
        var sanitisedLiveEventId = liveEventId.toString().replace("-", "");
        when(mockLiveEventClient.get(resourceGroup, accountName, sanitisedLiveEventId))
            .thenReturn(mockLiveEvent);

        var mockStreamingEndpointsClient = mock(StreamingEndpointsClient.class);
        var mockStreamingEndpointInner = mock(StreamingEndpointInner.class);
        when(mockStreamingEndpointInner.hostname()).thenReturn("pre-example.com");

        when(amsClient.getStreamingEndpoints()).thenReturn(mockStreamingEndpointsClient);

        when(mockStreamingEndpointsClient
                 .create(eq(resourceGroup),
                         eq(accountName),
                         eq("c154d36ecab44aaaa4c711d"),
                         any(StreamingEndpointInner.class))
        ).thenReturn(mockStreamingEndpointInner);

        var mockStreamingLocatorsClient = mock(StreamingLocatorsClient.class);
        when(amsClient.getStreamingLocators()).thenReturn(mockStreamingLocatorsClient);

        var mockListPathsResponseInner = mock(ListPathsResponseInner.class);
        var mockStreamingPath1 = mock(StreamingPath.class);
        when(mockStreamingPath1.paths()).thenReturn(List.of());
        var mockStreamingPath2 = mock(StreamingPath.class);
        when(mockStreamingPath2.paths()).thenReturn(List.of());
        when(mockListPathsResponseInner.streamingPaths()).thenReturn(List.of(mockStreamingPath1, mockStreamingPath2));
        when(mockStreamingLocatorsClient.listPaths(resourceGroup, accountName, sanitisedLiveEventId))
            .thenReturn(mockListPathsResponseInner);

        assertThrows(RuntimeException.class, () -> mediaService.playLiveEvent(liveEventId));
    }
}
