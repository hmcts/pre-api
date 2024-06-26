package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AssetsClient;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.LiveEventsClient;
import com.azure.resourcemanager.mediaservices.fluent.LiveOutputsClient;
import com.azure.resourcemanager.mediaservices.fluent.StreamingEndpointsClient;
import com.azure.resourcemanager.mediaservices.fluent.StreamingLocatorsClient;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.ListPathsResponseInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveOutputInner;
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
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.exception.AMSLiveEventNotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureMediaService.class)
public class AzureMediaServiceTest {
    @MockBean
    private AzureMediaServices amsClient;

    private final String resourceGroup = "test-resource-group";
    private final String accountName = "test-account-name";
    private final String ingestSa = "test-sa";
    private final String env = "test-env";

    private AzureMediaService mediaService;

    private CaptureSessionDTO captureSession;

    @BeforeEach
    void setUp() {
        mediaService = new AzureMediaService(
            resourceGroup,
            accountName,
            ingestSa,
            env,
            amsClient
        );

        captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBookingId(UUID.randomUUID());
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
        var mockAssetsClient = mock(AssetsClient.class);
        var amsError = mockAmsError(404);
        when(amsClient.getAssets()).thenReturn(mockAssetsClient);
        when(amsClient.getAssets().get(resourceGroup, accountName, name))
            .thenThrow(amsError);

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
        var mockAssetsClient = mock(AssetsClient.class);
        var amsError = mockAmsError(400);

        when(amsClient.getAssets()).thenReturn(mockAssetsClient);
        when(amsClient.getAssets().get(resourceGroup, accountName, name))
            .thenThrow(amsError);

        var message = assertThrows(
            ManagementException.class,
            () -> mediaService.getAsset(name)
        ).getMessage();

        assertThat(message).isEqualTo("error");
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

    @DisplayName("Should throw Unsupported Operation Exception when method is not defined")
    @Test
    void unsupportedOperationException() {
        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.playAsset("test-asset-name")
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> mediaService.importAsset("test-asset-name")
        );
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

    @DisplayName("Should return the capture session when successfully started the live event")
    @Test
    void startLiveEventSuccess() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        when(mockLiveEvent.resourceState())
            .thenReturn(
                LiveEventResourceState.STARTING,
                LiveEventResourceState.STARTING,
                LiveEventResourceState.RUNNING,
                LiveEventResourceState.RUNNING
            );
        when(mockLiveEvent.input())
            .thenReturn(
                new LiveEventInput()
                    .withEndpoints(List.of(
                        new LiveEventEndpoint()
                            .withProtocol("RTMP")
                            .withUrl("rtmp://some-rtmp-address"),
                        new LiveEventEndpoint()
                            .withProtocol("RTMP")
                            .withUrl("rtmps://some-rtmp-address")
                    ))
            );

        var ingest = mediaService.startLiveEvent(captureSession);
        assertThat(ingest).isEqualTo("rtmps://some-rtmp-address");

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(4)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).start(any(), any(), any());
    }

    @DisplayName("Should return the capture session when successfully started the live event")
    @Test
    void startLiveEventLiveEventConflictSuccess() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        var amsError = mockAmsError(409);
        when(liveEventClient.create(eq(resourceGroup), eq(accountName), eq(liveEventName), any()))
            .thenThrow(amsError);
        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        when(mockLiveEvent.resourceState())
            .thenReturn(
                LiveEventResourceState.STARTING,
                LiveEventResourceState.STARTING,
                LiveEventResourceState.RUNNING,
                LiveEventResourceState.RUNNING
            );
        when(mockLiveEvent.input())
            .thenReturn(
                new LiveEventInput()
                    .withEndpoints(List.of(
                        new LiveEventEndpoint()
                            .withProtocol("RTMP")
                            .withUrl("rtmp://some-rtmp-address"),
                        new LiveEventEndpoint()
                            .withProtocol("RTMP")
                            .withUrl("rtmps://some-rtmp-address")
                    ))
            );

        var ingest = mediaService.startLiveEvent(captureSession);
        assertThat(ingest).isEqualTo("rtmps://some-rtmp-address");

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(4)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).start(any(), any(), any());
    }

    @DisplayName("Should throw not found error when live event cannot be found after creation")
    @Test
    void startLiveEventNotFoundAfterCreate() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var liveEventClient = mockLiveEventClient();

        var amsError = mockAmsError(404);
        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenThrow(amsError);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.startLiveEvent(captureSession)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Live event: " + liveEventName);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
    }

    @DisplayName("Should throw 409 error when asset already exists")
    @Test
    void startLiveEventAssetConflict() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();

        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        var amsError = mockAmsError(409);
        when(assetsClient.createOrUpdate(eq(resourceGroup), eq(accountName), eq(liveEventName), any(AssetInner.class)))
            .thenThrow(amsError);

        var message = assertThrows(
            ConflictException.class,
            () -> mediaService.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Asset: " + liveEventName);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
    }

    @DisplayName("Should throw 409 error when live output already exists")
    @Test
    void startLiveEventLiveOutputConflict() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        var amsError = mockAmsError(409);
        when(liveOutputClient.create(eq(resourceGroup), eq(accountName), eq(liveEventName), eq(liveEventName), any(
            LiveOutputInner.class))).thenThrow(amsError);

        var message = assertThrows(
            ConflictException.class,
            () -> mediaService.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Live Output: " + liveEventName);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
    }


    @DisplayName("Should throw 404 error when creating a live output but cannot find live event")
    @Test
    void startLiveEventLiveOutputLiveEventNotFound() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        var amsError = mockAmsError(404);
        when(liveOutputClient.create(eq(resourceGroup), eq(accountName), eq(liveEventName), eq(liveEventName), any(
            LiveOutputInner.class))).thenThrow(amsError);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
    }

    @DisplayName("Should throw 404 error when attempting to start live event that cannot be found (after setup)")
    @Test
    void startLiveEventStartNotFound() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        var amsError = mockAmsError(404);
        doThrow(amsError).when(liveEventClient).start(resourceGroup, accountName, liveEventName);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).start(any(), any(), any());
    }

    @DisplayName("Should fail to start a live event with blank ingest url")
    @Test
    void startLiveEventBlankIngestUrl() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var liveEventClient = mockLiveEventClient();
        var mockLiveEvent = mock(LiveEventInner.class);
        var assetsClient = mockAssetsClient();
        var liveOutputClient = mockLiveOutputClient();

        when(liveEventClient.get(
            resourceGroup,
            accountName,
            liveEventName
        )).thenReturn(mockLiveEvent);
        when(mockLiveEvent.resourceState())
            .thenReturn(
                LiveEventResourceState.STARTING,
                LiveEventResourceState.STARTING,
                LiveEventResourceState.RUNNING,
                LiveEventResourceState.RUNNING
            );
        when(mockLiveEvent.input())
            .thenReturn(
                new LiveEventInput()
                    .withEndpoints(List.of())
            );

        var message = assertThrows(
            UnknownServerException.class,
            () -> mediaService.startLiveEvent(captureSession)
        ).getMessage();

        assertThat("Unknown Server Exception: Unable to get ingest URL from AMS. No error of exception thrown")
            .isEqualTo(message);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(4)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).create(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).start(any(), any(), any());
    }

    private LiveEventsClient mockLiveEventClient() {
        var client = mock(LiveEventsClient.class);
        when(amsClient.getLiveEvents()).thenReturn(client);
        return client;
    }

    private AssetsClient mockAssetsClient() {
        var client = mock(AssetsClient.class);
        when(amsClient.getAssets()).thenReturn(client);
        return client;
    }

    private LiveOutputsClient mockLiveOutputClient() {
        var client = mock(LiveOutputsClient.class);
        when(amsClient.getLiveOutputs()).thenReturn(client);
        return client;
    }

    private ManagementException mockAmsError(int status) {
        var response = mock(HttpResponse.class);
        when(response.getStatusCode()).thenReturn(status);
        return new ManagementException("error", response);
    }
}
