package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.http.HttpResponse;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AssetsClient;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.ContentKeyPoliciesClient;
import com.azure.resourcemanager.mediaservices.fluent.JobsClient;
import com.azure.resourcemanager.mediaservices.fluent.LiveEventsClient;
import com.azure.resourcemanager.mediaservices.fluent.LiveOutputsClient;
import com.azure.resourcemanager.mediaservices.fluent.StreamingEndpointsClient;
import com.azure.resourcemanager.mediaservices.fluent.StreamingLocatorsClient;
import com.azure.resourcemanager.mediaservices.fluent.StreamingPoliciesClient;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.ContentKeyPolicyInner;
import com.azure.resourcemanager.mediaservices.fluent.models.JobInner;
import com.azure.resourcemanager.mediaservices.fluent.models.ListPathsResponseInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveOutputInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingEndpointInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingLocatorInner;
import com.azure.resourcemanager.mediaservices.fluent.models.StreamingPolicyInner;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import com.azure.resourcemanager.mediaservices.models.StreamingEndpointResourceState;
import com.azure.resourcemanager.mediaservices.models.StreamingPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.AMSLiveEventNotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureMediaService.class)
public class AzureMediaServiceTest {
    private final String resourceGroup = "test-resource-group";
    private final String accountName = "test-account-name";
    private final String ingestSa = "test-ingest-sa";
    private final String finalSa = "test-final-sa";
    private final String env = "test-env";
    private final String issuer = "test-issuer";
    private final String symmetricKey = "abc";

    private AzureMediaService mediaService;
    private CaptureSessionDTO captureSession;

    @MockBean
    private AzureMediaServices amsClient;

    @MockBean
    private AzureFinalStorageService azureFinalStorageService;

    @BeforeEach
    void setUp() {
        mediaService = new AzureMediaService(
            resourceGroup,
            accountName,
            ingestSa,
            finalSa,
            env,
            issuer,
            symmetricKey,
            amsClient,
            azureFinalStorageService
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

    @DisplayName("Should accept a request to import an asset and return a job response for encoding to mp4")
    @Test
    void importAssetSuccess() throws InterruptedException {
        var mockJobClient = mock(JobsClient.class);
        var mockJob = mock(JobInner.class);
        when(amsClient.getJobs()).thenReturn(mockJobClient);
        when(amsClient.getJobs().get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);

        var mockAssetsClient = mock(AssetsClient.class);
        when(amsClient.getAssets()).thenReturn(mockAssetsClient);

        var generateAssetDTO = new GenerateAssetDTO(
            "my-source-container",
            "my-destination-container",
            "tmp-asset",
            "final-asset",
            "unit test import asset"
        );

        var result = mediaService.importAsset(generateAssetDTO);

        assertThat(result.getJobStatus()).isEqualTo("Finished");

        var sourceContainerArgument = ArgumentCaptor.forClass(AssetInner.class);

        verify(mockAssetsClient, times(1))
            .createOrUpdate(
                eq(resourceGroup),
                eq(accountName),
                eq(generateAssetDTO.getTempAsset()),
                sourceContainerArgument.capture()
            );

        assertThat(sourceContainerArgument.getValue().container()).isEqualTo(generateAssetDTO.getSourceContainer());

        var destinationContainerArgument = ArgumentCaptor.forClass(AssetInner.class);

        verify(mockAssetsClient, times(1))
            .createOrUpdate(
                eq(resourceGroup),
                eq(accountName),
                eq(generateAssetDTO.getFinalAsset()),
                destinationContainerArgument.capture()
            );

        assertThat(destinationContainerArgument.getValue().container())
            .isEqualTo(generateAssetDTO.getDestinationContainer());

        ArgumentCaptor<JobInner> jobInnerArgument = ArgumentCaptor.forClass(JobInner.class);

        verify(mockJobClient, times(1))
            .create(
                eq(resourceGroup),
                eq(accountName),
                eq("EncodeToMP4"),
                anyString(),
                jobInnerArgument.capture()
            );

        JobInputAsset ji = (JobInputAsset) jobInnerArgument.getValue().input();


        assertThat(((JobInputAsset) jobInnerArgument.getValue().input()).assetName())
            .isEqualTo(generateAssetDTO.getTempAsset());
        assertThat(((JobOutputAsset) jobInnerArgument.getValue().outputs().getFirst()).assetName())
            .isEqualTo(generateAssetDTO.getFinalAsset());
    }

    @DisplayName("Should accept a request to import an asset and handle a failed job to encode to mp4")
    @Test
    void importAssetJobFailed() throws InterruptedException {
        var mockJobClient = mock(JobsClient.class);
        var mockJob = mock(JobInner.class);
        when(amsClient.getJobs()).thenReturn(mockJobClient);
        when(amsClient.getJobs().get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.ERROR);

        var mockAssetsClient = mock(AssetsClient.class);
        when(amsClient.getAssets()).thenReturn(mockAssetsClient);

        var generateAssetDTO = new GenerateAssetDTO(
            "my-source-container",
            "my-destination-container",
            "tmp-asset",
            "final-asset",
            "unit test import asset"
        );

        var result = mediaService.importAsset(generateAssetDTO);

        assertThat(result.getJobStatus()).isEqualTo("Error");

        var sourceContainerArgument = ArgumentCaptor.forClass(AssetInner.class);

        verify(mockAssetsClient, times(1))
            .createOrUpdate(
                eq(resourceGroup),
                eq(accountName),
                eq(generateAssetDTO.getTempAsset()),
                sourceContainerArgument.capture()
            );

        assertThat(sourceContainerArgument.getValue().container()).isEqualTo(generateAssetDTO.getSourceContainer());

        var destinationContainerArgument = ArgumentCaptor.forClass(AssetInner.class);

        verify(mockAssetsClient, times(1))
            .createOrUpdate(
                eq(resourceGroup),
                eq(accountName),
                eq(generateAssetDTO.getFinalAsset()),
                destinationContainerArgument.capture()
            );

        assertThat(destinationContainerArgument.getValue().container())
            .isEqualTo(generateAssetDTO.getDestinationContainer());

        ArgumentCaptor<JobInner> jobInnerArgument = ArgumentCaptor.forClass(JobInner.class);

        verify(mockJobClient, times(1))
            .create(
                eq(resourceGroup),
                eq(accountName),
                eq("EncodeToMP4"),
                anyString(),
                jobInnerArgument.capture()
            );

        assertThat(((JobInputAsset) jobInnerArgument.getValue().input()).assetName())
            .isEqualTo(generateAssetDTO.getTempAsset());
        assertThat(((JobOutputAsset) jobInnerArgument.getValue().outputs().getFirst()).assetName())
            .isEqualTo(generateAssetDTO.getFinalAsset());
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
                 .create(
                     eq(resourceGroup),
                     eq(accountName),
                     eq("c154d36ecab44aaaa4c711d"),
                     any(StreamingEndpointInner.class)
                 )
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
            .create(
                eq(resourceGroup),
                eq(accountName),
                eq(sanitisedLiveEventId),
                any()
            );
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
                 .create(
                     eq(resourceGroup),
                     eq(accountName),
                     eq("c154d36ecab44aaaa4c711d"),
                     any(StreamingEndpointInner.class)
                 )
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
    void playLiveEventLiveEventNotRunningException() {
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
                 .create(
                     eq(resourceGroup),
                     eq(accountName),
                     eq("c154d36ecab44aaaa4c711d"),
                     any(StreamingEndpointInner.class)
                 )
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
                 .create(
                     eq(resourceGroup),
                     eq(accountName),
                     eq("c154d36ecab44aaaa4c711d"),
                     any(StreamingEndpointInner.class)
                 )
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
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    void startLiveEventSuccess() {
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

        mediaService.startLiveEvent(captureSession);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).beginCreate(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).beginStart(any(), any(), any());
    }

    @DisplayName("Should return the capture session when successfully started the live event")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    void startLiveEventLiveEventConflictSuccess() {
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

        mediaService.startLiveEvent(captureSession);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).beginCreate(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).beginStart(any(), any(), any());
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
        when(liveOutputClient.beginCreate(eq(resourceGroup), eq(accountName), eq(liveEventName), eq(liveEventName), any(
            LiveOutputInner.class))).thenThrow(amsError);

        var message = assertThrows(
            ConflictException.class,
            () -> mediaService.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Live Output: " + liveEventName);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).beginCreate(any(), any(), any(), any(), any());
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
        when(liveOutputClient.beginCreate(eq(resourceGroup), eq(accountName), eq(liveEventName), eq(liveEventName), any(
            LiveOutputInner.class))).thenThrow(amsError);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).beginCreate(any(), any(), any(), any(), any());
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
        doThrow(amsError).when(liveEventClient).beginStart(resourceGroup, accountName, liveEventName);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        verify(liveEventClient, times(1)).create(any(), any(), any(), any());
        verify(liveEventClient, times(1)).get(any(), any(), any());
        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(liveOutputClient, times(1)).beginCreate(any(), any(), any(), any(), any());
        verify(liveEventClient, times(1)).beginStart(any(), any(), any());
    }

    @DisplayName("Should successfully stop live event when there is not a recording found")
    @Test
    void stopLiveEventNoRecording() throws InterruptedException {
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var liveOutputClient = mockLiveOutputClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.PROCESSING, JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);

        assertThat(mediaService.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.NO_RECORDING);

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(2)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
        verify(liveEventClient, times(1)).delete(any(), any(), any());
        verify(streamingEndpointClient, times(1)).stop(any(), any(), any());
        verify(streamingEndpointClient, times(1)).delete(any(), any(), any());
        verify(streamingLocatorClient, times(1)).delete(any(), any(), any());
        verify(liveOutputClient, times(1)).delete(any(), any(), any(), any());
    }

    private StreamingEndpointsClient mockStreamingEndpointClient() {
        var client = mock(StreamingEndpointsClient.class);
        when(amsClient.getStreamingEndpoints()).thenReturn(client);
        return client;
    }

    private StreamingLocatorsClient mockStreamingLocatorClient() {
        var client = mock(StreamingLocatorsClient.class);
        when(amsClient.getStreamingLocators()).thenReturn(client);
        return client;
    }

    private ContentKeyPoliciesClient mockContentKeyPoliciesClient() {
        var client = mock(ContentKeyPoliciesClient.class);
        when(amsClient.getContentKeyPolicies()).thenReturn(client);
        return client;
    }

    private JobsClient mockJobsClient() {
        var client = mock(JobsClient.class);
        when(amsClient.getJobs()).thenReturn(client);
        return client;
    }

    private ManagementException mockAmsError(int status) {
        var response = mock(HttpResponse.class);
        when(response.getStatusCode()).thenReturn(status);
        return new ManagementException("error", response);
    }

    @DisplayName("Should successfully stop live event when there is a recording found")
    @Test
    void stopLiveEventRecordingAvailable() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var liveOutputClient = mockLiveOutputClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.PROCESSING, JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);

        assertThat(mediaService.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.RECORDING_AVAILABLE);

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(2)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
        verify(liveEventClient, times(1)).delete(any(), any(), any());
        verify(streamingEndpointClient, times(1)).stop(any(), any(), any());
        verify(streamingEndpointClient, times(1)).delete(any(), any(), any());
        verify(streamingLocatorClient, times(1)).delete(any(), any(), any());
        verify(liveOutputClient, times(1)).delete(any(), any(), any(), any());
    }

    @DisplayName("Should throw error when error occurs creating asset")
    @Test
    void stopLiveEventAssetCreateError() {
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();

        var e = mockAmsError(500);
        when(assetsClient.createOrUpdate(any(), any(), any(), any()))
            .thenThrow(e);

        assertThrows(
            ManagementException.class,
            () -> mediaService.stopLiveEvent(captureSession, recordingId)
        );

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
    }

    @DisplayName("Should throw error when error occurs creating encode to mp4 job")
    @Test
    void stopLiveEventTransformCreateError() {
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();

        var e = mockAmsError(404);
        when(jobsClient.create(any(), any(), any(), any(), any()))
            .thenThrow(e);

        assertThrows(
            ManagementException.class,
            () -> mediaService.stopLiveEvent(captureSession, recordingId)
        );

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
    }

    @DisplayName("Should throw not found when live event cannot be found to stop and delete")
    @Test
    void stopLiveEventLiveEventNotFound() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);
        var e = mockAmsError(404);
        doThrow(e).when(liveEventClient).stop(any(), any(), any(), any());

        var message = assertThrows(
            AMSLiveEventNotFoundException.class,
            () -> mediaService.stopLiveEvent(captureSession, recordingId)
        ).getMessage();
        assertThat(message).isEqualTo("AMS Live event not found with id " + liveEventName);

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(1)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
    }

    @DisplayName("Should throw management exception when error occurs stopping/deleting live event")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    void stopLiveEventLiveEventManagementError() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);
        var e = mockAmsError(500);
        doThrow(e).when(liveEventClient).stop(any(), any(), any(), any());

        assertThrows(
            ManagementException.class,
            () -> mediaService.stopLiveEvent(captureSession, recordingId)
        );

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(1)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
    }

    @DisplayName("Should successfully stop live event when there is not a streaming endpoint found to stop/delete")
    @Test
    void stopLiveEventNoStreamingEndpointFoundSuccess() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var liveOutputClient = mockLiveOutputClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);
        var e = mockAmsError(404);
        doThrow(e).when(streamingEndpointClient).stop(any(), any(), any());

        assertThat(mediaService.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.NO_RECORDING);

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(1)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
        verify(liveEventClient, times(1)).delete(any(), any(), any());
        verify(streamingEndpointClient, times(1)).stop(any(), any(), any());
        verify(streamingLocatorClient, times(1)).delete(any(), any(), any());
        verify(liveOutputClient, times(1)).delete(any(), any(), any(), any());
    }

    @DisplayName("Should throw error when an error occurs stopping/deleting streaming endpoint")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    void stopLiveEventStreamingEndpointError() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);
        var e = mockAmsError(500);
        doThrow(e).when(streamingEndpointClient).stop(any(), any(), any());

        assertThrows(
            ManagementException.class,
            () -> mediaService.stopLiveEvent(captureSession, recordingId)
        );

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(1)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
        verify(liveEventClient, times(1)).delete(any(), any(), any());
        verify(streamingEndpointClient, times(1)).stop(any(), any(), any());
    }

    @DisplayName("Should successfully stop live event when there is not a streaming locator found to delete")
    @Test
    void stopLiveEventNoStreamingLocatorFoundSuccess() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var liveOutputClient = mockLiveOutputClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);
        var e = mockAmsError(404);
        doThrow(e).when(streamingLocatorClient).delete(any(), any(), any());

        assertThat(mediaService.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.NO_RECORDING);

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(1)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
        verify(liveEventClient, times(1)).delete(any(), any(), any());
        verify(streamingEndpointClient, times(1)).stop(any(), any(), any());
        verify(streamingEndpointClient, times(1)).delete(any(), any(), any());
        verify(streamingLocatorClient, times(1)).delete(any(), any(), any());
        verify(liveOutputClient, times(1)).delete(any(), any(), any(), any());
    }

    @DisplayName("Should throw error when error occurs deleting streaming locator")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    void stopLiveEventStreamingLocatorError() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();
        var streamingLocatorClient = mockStreamingLocatorClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);
        var e = mockAmsError(500);
        doThrow(e).when(streamingLocatorClient).delete(any(), any(), any());

        assertThrows(
            ManagementException.class,
            () -> mediaService.stopLiveEvent(captureSession, recordingId)
        );

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(1)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
        verify(liveEventClient, times(1)).delete(any(), any(), any());
        verify(streamingEndpointClient, times(1)).stop(any(), any(), any());
        verify(streamingEndpointClient, times(1)).delete(any(), any(), any());
        verify(streamingLocatorClient, times(1)).delete(any(), any(), any());
    }

    @DisplayName("Should successfully stop live event when there is not a live output found to delete")
    @Test
    void stopLiveEventLiveOutputNotFoundSuccess() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var liveOutputClient = mockLiveOutputClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);
        var e = mockAmsError(404);
        doThrow(e).when(liveOutputClient).delete(any(), any(), any(), any());

        assertThat(mediaService.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.NO_RECORDING);

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(1)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
        verify(liveEventClient, times(1)).delete(any(), any(), any());
        verify(streamingEndpointClient, times(1)).stop(any(), any(), any());
        verify(streamingEndpointClient, times(1)).delete(any(), any(), any());
        verify(streamingLocatorClient, times(1)).delete(any(), any(), any());
        verify(liveOutputClient, times(1)).delete(any(), any(), any(), any());
    }

    @DisplayName("Should throw error when error occurs attempting to delete live output")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    void stopLiveEventLiveOutputError() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var liveOutputClient = mockLiveOutputClient();

        when(jobsClient.get(eq(resourceGroup), eq(accountName), eq("EncodeToMP4"), anyString()))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);
        var e = mockAmsError(500);
        doThrow(e).when(liveOutputClient).delete(any(), any(), any(), any());

        assertThrows(
            ManagementException.class,
            () -> mediaService.stopLiveEvent(captureSession, recordingId)
        );

        verify(assetsClient, times(1)).createOrUpdate(any(), any(), any(), any());
        verify(jobsClient, times(1)).create(any(), any(), any(), any(), any());
        verify(jobsClient, times(1)).get(any(), any(), any(), any());
        verify(azureFinalStorageService, times(1)).doesIsmFileExist(recordingId.toString());
        verify(liveEventClient, times(1)).stop(any(), any(), any(), any());
        verify(liveEventClient, times(1)).delete(any(), any(), any());
        verify(streamingEndpointClient, times(1)).stop(any(), any(), any());
        verify(streamingEndpointClient, times(1)).delete(any(), any(), any());
        verify(streamingLocatorClient, times(1)).delete(any(), any(), any());
        verify(liveOutputClient, times(1)).delete(any(), any(), any(), any());
    }

    @DisplayName("Should delete all streaming locators and Content Key Policies")
    @Test
    @SuppressWarnings("unchecked")
    void testDeleteAllStreamingLocatorsAndContentKeyPolicies() {
        var streamingLocatorClient = mockStreamingLocatorClient();

        var mockIterable = mock(PagedIterable.class);
        var mockIterator = mock(Iterator.class);

        when(streamingLocatorClient.list(resourceGroup, accountName))
            .thenReturn(mockIterable);

        Mockito.doCallRealMethod().when(mockIterable).forEach(any(Consumer.class));

        when(mockIterable.iterator()).thenReturn(mockIterator);

        var sli1 = mock(StreamingLocatorInner.class);
        when(sli1.name()).thenReturn("asset1");
        var sli2 = mock(StreamingLocatorInner.class);
        when(sli2.name()).thenReturn("asset2");
        var sli3 = mock(StreamingLocatorInner.class);
        when(sli3.name()).thenReturn("asset3");

        when(mockIterator.hasNext()).thenReturn(true, true, true, false);
        when(mockIterator.next()).thenReturn(sli1, sli2, sli3);

        var contentKeyPolicyClient = mockContentKeyPoliciesClient();

        var mockIterableKeyPolicy = mock(PagedIterable.class);
        var mockIteratorKeyPolicy = mock(Iterator.class);

        when(contentKeyPolicyClient.list(resourceGroup, accountName))
            .thenReturn(mockIterableKeyPolicy);

        Mockito.doCallRealMethod().when(mockIterableKeyPolicy).forEach(any(Consumer.class));

        when(mockIterableKeyPolicy.iterator()).thenReturn(mockIteratorKeyPolicy);

        var ckp1 = mock(ContentKeyPolicyInner.class);
        when(ckp1.name()).thenReturn("policy1");
        var ckp2 = mock(ContentKeyPolicyInner.class);
        when(ckp2.name()).thenReturn("policy2");
        var ckp3 = mock(ContentKeyPolicyInner.class);
        when(ckp3.name()).thenReturn("policy3");

        when(mockIteratorKeyPolicy.hasNext()).thenReturn(true, true, true, false);
        when(mockIteratorKeyPolicy.next()).thenReturn(ckp1, ckp2, ckp3);

        mediaService.deleteAllStreamingLocatorsAndContentKeyPolicies();

        verify(streamingLocatorClient, times(1)).list(resourceGroup, accountName);

        verify(streamingLocatorClient, times(1)).delete(resourceGroup, accountName, "asset1");
        verify(streamingLocatorClient, times(1)).delete(resourceGroup, accountName, "asset2");
        verify(streamingLocatorClient, times(1)).delete(resourceGroup, accountName, "asset3");

        verify(contentKeyPolicyClient, times(1)).list(resourceGroup, accountName);

        verify(contentKeyPolicyClient, times(1)).delete(resourceGroup, accountName, "policy1");
        verify(contentKeyPolicyClient, times(1)).delete(resourceGroup, accountName, "policy2");
        verify(contentKeyPolicyClient, times(1)).delete(resourceGroup, accountName, "policy3");
    }

    @DisplayName("Should return playback details for asset")
    @Test
    void playAssetSuccess() {
        var assetName = "example-asset_output";
        var userId = UUID.randomUUID().toString();
        var assetClient = mockAssetsClient();
        var contentKeyPolicyClient = mockContentKeyPoliciesClient();
        var streamingPolicyClient = mockStreamingPoliciesClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var streamingEndpointClient = mockStreamingEndpointClient();

        when(assetClient.get(resourceGroup, accountName, assetName))
            .thenReturn(mock(AssetInner.class));
        when(contentKeyPolicyClient.get(resourceGroup, accountName, userId))
            .thenReturn(mock(ContentKeyPolicyInner.class));
        when(streamingPolicyClient.get(resourceGroup, accountName, "Predefined_ClearKey"))
            .thenReturn(mock(StreamingPolicyInner.class));
        when(streamingLocatorClient.create(
                 eq(resourceGroup),
                 eq(accountName),
                 eq("Predefined_ClearKey"),
                 any(StreamingLocatorInner.class)
             )
        ).thenReturn(mock(StreamingLocatorInner.class));
        var mockEndpoint = mock(StreamingEndpointInner.class);
        when(streamingEndpointClient.get(resourceGroup, accountName, "default"))
            .thenReturn(mockEndpoint);
        when(mockEndpoint.resourceState()).thenReturn(StreamingEndpointResourceState.RUNNING);
        when(mockEndpoint.hostname()).thenReturn("example.com");
        var streamingPaths = List.of(
            new StreamingPath()
                .withPaths(List.of("/example.ism"))
        );
        when(streamingLocatorClient.listPaths(resourceGroup, accountName, userId))
            .thenReturn(new ListPathsResponseInner().withStreamingPaths(streamingPaths));

        var response = mediaService.playAsset(assetName, userId);

        assertThat(response.getDashUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getHlsUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getLicenseUrl()).isNull();
        assertThat(response.getToken()).isNotNull();

        verify(assetClient, times(1)).get(resourceGroup, accountName, assetName);
        verify(contentKeyPolicyClient, times(1)).get(resourceGroup, accountName, userId);
        verify(streamingPolicyClient, times(1)).get(resourceGroup, accountName, "Predefined_ClearKey");
        verify(streamingLocatorClient, times(1))
            .create(eq(resourceGroup), eq(accountName), eq(userId), any(StreamingLocatorInner.class));
        verify(streamingEndpointClient, times(1)).get(resourceGroup, accountName, "default");
        verify(streamingLocatorClient, times(1)).listPaths(resourceGroup, accountName, userId);
    }

    @DisplayName("Should throw not found when asset does not exist")
    @Test
    void playAssetAssetNotFound() {
        var assetName = "example-asset_output";
        var userId = UUID.randomUUID().toString();
        var assetClient = mockAssetsClient();

        var amsError = mockAmsError(404);
        ;
        doThrow(amsError).when(assetClient).get(resourceGroup, accountName, assetName);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.playAsset(assetName, userId)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Asset with name: " + assetName);

        verify(assetClient, times(1)).get(resourceGroup, accountName, assetName);
    }

    @DisplayName("Should create new content key policy if one doesn't exist and the return playback data")
    @Test
    void playAssetSuccessWhenContentKeyPolicyDoesNotExist() {
        var assetName = "example-asset_output";
        var userId = UUID.randomUUID().toString();
        var assetClient = mockAssetsClient();
        var contentKeyPolicyClient = mockContentKeyPoliciesClient();
        var streamingPolicyClient = mockStreamingPoliciesClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var streamingEndpointClient = mockStreamingEndpointClient();

        when(assetClient.get(resourceGroup, accountName, assetName))
            .thenReturn(mock(AssetInner.class));
        var amsError = mockAmsError(404);
        doThrow(amsError).when(contentKeyPolicyClient).get(resourceGroup, accountName, userId);
        when(streamingLocatorClient.create(
                 eq(resourceGroup),
                 eq(accountName),
                 eq("Predefined_ClearKey"),
                 any(StreamingLocatorInner.class)
             )
        ).thenReturn(mock(StreamingLocatorInner.class));
        var mockEndpoint = mock(StreamingEndpointInner.class);
        when(streamingEndpointClient.get(resourceGroup, accountName, "default"))
            .thenReturn(mockEndpoint);
        when(mockEndpoint.resourceState()).thenReturn(StreamingEndpointResourceState.RUNNING);
        when(mockEndpoint.hostname()).thenReturn("example.com");
        var streamingPaths = List.of(
            new StreamingPath()
                .withPaths(List.of("/example.ism"))
        );
        when(streamingLocatorClient.listPaths(resourceGroup, accountName, userId))
            .thenReturn(new ListPathsResponseInner().withStreamingPaths(streamingPaths));

        var response = mediaService.playAsset(assetName, userId);

        assertThat(response.getDashUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getHlsUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getLicenseUrl()).isNull();
        assertThat(response.getToken()).isNotNull();

        verify(assetClient, times(1)).get(resourceGroup, accountName, assetName);
        verify(contentKeyPolicyClient, times(1)).get(resourceGroup, accountName, userId);
        verify(contentKeyPolicyClient, times(1))
            .createOrUpdate(eq(resourceGroup), eq(accountName), eq(userId), any(ContentKeyPolicyInner.class));
        verify(streamingPolicyClient, times(1)).get(resourceGroup, accountName, "Predefined_ClearKey");
        verify(streamingLocatorClient, times(1))
            .create(eq(resourceGroup), eq(accountName), eq(userId), any(StreamingLocatorInner.class));
        verify(streamingEndpointClient, times(1)).get(resourceGroup, accountName, "default");
        verify(streamingLocatorClient, times(1)).listPaths(resourceGroup, accountName, userId);
    }

    @DisplayName("Should return playback details for asset when streaming policy does not exist")
    @Test
    void playAssetSuccessWhenStreamingPolicyDoesNotExist() {
        var assetName = "example-asset_output";
        var userId = UUID.randomUUID().toString();
        var assetClient = mockAssetsClient();
        var contentKeyPolicyClient = mockContentKeyPoliciesClient();
        var streamingPolicyClient = mockStreamingPoliciesClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var streamingEndpointClient = mockStreamingEndpointClient();

        when(assetClient.get(resourceGroup, accountName, assetName))
            .thenReturn(mock(AssetInner.class));
        when(contentKeyPolicyClient.get(resourceGroup, accountName, userId))
            .thenReturn(mock(ContentKeyPolicyInner.class));
        var amsError = mockAmsError(404);
        doThrow(amsError).when(streamingPolicyClient).get(resourceGroup, accountName, "Predefined_ClearKey");
        when(streamingLocatorClient.create(
                 eq(resourceGroup),
                 eq(accountName),
                 eq("Predefined_ClearKey"),
                 any(StreamingLocatorInner.class)
             )
        ).thenReturn(mock(StreamingLocatorInner.class));
        var mockEndpoint = mock(StreamingEndpointInner.class);
        when(streamingEndpointClient.get(resourceGroup, accountName, "default"))
            .thenReturn(mockEndpoint);
        when(mockEndpoint.resourceState()).thenReturn(StreamingEndpointResourceState.RUNNING);
        when(mockEndpoint.hostname()).thenReturn("example.com");
        var streamingPaths = List.of(
            new StreamingPath()
                .withPaths(List.of("/example.ism"))
        );
        when(streamingLocatorClient.listPaths(resourceGroup, accountName, userId))
            .thenReturn(new ListPathsResponseInner().withStreamingPaths(streamingPaths));

        var response = mediaService.playAsset(assetName, userId);

        assertThat(response.getDashUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getHlsUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getLicenseUrl()).isNull();
        assertThat(response.getToken()).isNotNull();

        verify(assetClient, times(1)).get(resourceGroup, accountName, assetName);
        verify(contentKeyPolicyClient, times(1)).get(resourceGroup, accountName, userId);
        verify(streamingPolicyClient, times(1)).get(resourceGroup, accountName, "Predefined_ClearKey");
        verify(streamingPolicyClient, times(1))
            .create(eq(resourceGroup), eq(accountName), eq("Predefined_ClearKey"), any(StreamingPolicyInner.class));
        verify(streamingLocatorClient, times(1))
            .create(eq(resourceGroup), eq(accountName), eq(userId), any(StreamingLocatorInner.class));
        verify(streamingEndpointClient, times(1)).get(resourceGroup, accountName, "default");
        verify(streamingLocatorClient, times(1)).listPaths(resourceGroup, accountName, userId);
    }

    @DisplayName("Should throw error when a non 404 error occurs when deleting streaming locator")
    @Test
    void playAssetErrorDeleteStreamingLocator() {
        var assetName = "example-asset_output";
        var userId = UUID.randomUUID().toString();
        var assetClient = mockAssetsClient();
        var contentKeyPolicyClient = mockContentKeyPoliciesClient();
        var streamingPolicyClient = mockStreamingPoliciesClient();
        var streamingLocatorClient = mockStreamingLocatorClient();

        when(assetClient.get(resourceGroup, accountName, assetName))
            .thenReturn(mock(AssetInner.class));
        when(contentKeyPolicyClient.get(resourceGroup, accountName, userId))
            .thenReturn(mock(ContentKeyPolicyInner.class));
        when(streamingPolicyClient.get(resourceGroup, accountName, "Predefined_ClearKey"))
            .thenReturn(mock(StreamingPolicyInner.class));
        var amsError = mockAmsError(400);
        doThrow(amsError).when(streamingLocatorClient).delete(any(), any(), any());

        assertThrows(
            ManagementException.class,
            () -> mediaService.playAsset(assetName, userId)
        );

        verify(assetClient, times(1)).get(resourceGroup, accountName, assetName);
        verify(contentKeyPolicyClient, times(1)).get(resourceGroup, accountName, userId);
        verify(streamingPolicyClient, times(1)).get(resourceGroup, accountName, "Predefined_ClearKey");
        verify(streamingLocatorClient, times(1)).delete(any(), any(), any());
    }

    @DisplayName("Should return playback details and start streaming endpoint when not started")
    @Test
    void playAssetSuccessWhenStreamingEndpointNotStarted() {
        var assetName = "example-asset_output";
        var userId = UUID.randomUUID().toString();
        var assetClient = mockAssetsClient();
        var contentKeyPolicyClient = mockContentKeyPoliciesClient();
        var streamingPolicyClient = mockStreamingPoliciesClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var streamingEndpointClient = mockStreamingEndpointClient();

        when(assetClient.get(resourceGroup, accountName, assetName))
            .thenReturn(mock(AssetInner.class));
        when(contentKeyPolicyClient.get(resourceGroup, accountName, userId))
            .thenReturn(mock(ContentKeyPolicyInner.class));
        when(streamingPolicyClient.get(resourceGroup, accountName, "Predefined_ClearKey"))
            .thenReturn(mock(StreamingPolicyInner.class));
        when(streamingLocatorClient.create(
                 eq(resourceGroup),
                 eq(accountName),
                 eq("Predefined_ClearKey"),
                 any(StreamingLocatorInner.class)
             )
        ).thenReturn(mock(StreamingLocatorInner.class));
        var mockEndpoint = mock(StreamingEndpointInner.class);
        when(streamingEndpointClient.get(resourceGroup, accountName, "default"))
            .thenReturn(mockEndpoint);
        when(mockEndpoint.resourceState()).thenReturn(StreamingEndpointResourceState.STOPPED);
        when(mockEndpoint.hostname()).thenReturn("example.com");
        var streamingPaths = List.of(
            new StreamingPath()
                .withPaths(List.of("/example.ism"))
        );
        when(streamingLocatorClient.listPaths(resourceGroup, accountName, userId))
            .thenReturn(new ListPathsResponseInner().withStreamingPaths(streamingPaths));

        var response = mediaService.playAsset(assetName, userId);

        assertThat(response.getDashUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getHlsUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getLicenseUrl()).isNull();
        assertThat(response.getToken()).isNotNull();

        verify(assetClient, times(1)).get(resourceGroup, accountName, assetName);
        verify(contentKeyPolicyClient, times(1)).get(resourceGroup, accountName, userId);
        verify(streamingPolicyClient, times(1)).get(resourceGroup, accountName, "Predefined_ClearKey");
        verify(streamingLocatorClient, times(1))
            .create(eq(resourceGroup), eq(accountName), eq(userId), any(StreamingLocatorInner.class));
        verify(streamingEndpointClient, times(2)).get(resourceGroup, accountName, "default");
        verify(streamingEndpointClient, times(1)).start(resourceGroup, accountName, "default");
        verify(streamingLocatorClient, times(1)).listPaths(resourceGroup, accountName, userId);
    }

    @DisplayName("Should return playback details for asset when streaming endpoint does not exist")
    @Test
    void playAssetSuccessWhenEndpointDoesNotExist() {
        var assetName = "example-asset_output";
        var userId = UUID.randomUUID().toString();
        var assetClient = mockAssetsClient();
        var contentKeyPolicyClient = mockContentKeyPoliciesClient();
        var streamingPolicyClient = mockStreamingPoliciesClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var streamingEndpointClient = mockStreamingEndpointClient();

        when(assetClient.get(resourceGroup, accountName, assetName))
            .thenReturn(mock(AssetInner.class));
        when(contentKeyPolicyClient.get(resourceGroup, accountName, userId))
            .thenReturn(mock(ContentKeyPolicyInner.class));
        when(streamingPolicyClient.get(resourceGroup, accountName, "Predefined_ClearKey"))
            .thenReturn(mock(StreamingPolicyInner.class));
        when(streamingLocatorClient.create(
                 eq(resourceGroup),
                 eq(accountName),
                 eq("Predefined_ClearKey"),
                 any(StreamingLocatorInner.class)
             )
        ).thenReturn(mock(StreamingLocatorInner.class));
        var amsError = mockAmsError(404);
        doThrow(amsError).when(streamingEndpointClient).get(any(), any(), any());
        var mockEndpoint = mock(StreamingEndpointInner.class);
        when(streamingEndpointClient.create(
            eq(resourceGroup),
            eq(accountName),
            eq("default"),
            any(StreamingEndpointInner.class)
        )).thenReturn(mockEndpoint);
        when(mockEndpoint.resourceState()).thenReturn(StreamingEndpointResourceState.RUNNING);
        when(mockEndpoint.hostname()).thenReturn("example.com");
        var streamingPaths = List.of(
            new StreamingPath()
                .withPaths(List.of("/example.ism"))
        );
        when(streamingLocatorClient.listPaths(resourceGroup, accountName, userId))
            .thenReturn(new ListPathsResponseInner().withStreamingPaths(streamingPaths));

        var response = mediaService.playAsset(assetName, userId);

        assertThat(response.getDashUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getHlsUrl()).isEqualTo("https://example.com/example.ism");
        assertThat(response.getLicenseUrl()).isNull();
        assertThat(response.getToken()).isNotNull();

        verify(assetClient, times(1)).get(resourceGroup, accountName, assetName);
        verify(contentKeyPolicyClient, times(1)).get(resourceGroup, accountName, userId);
        verify(streamingPolicyClient, times(1)).get(resourceGroup, accountName, "Predefined_ClearKey");
        verify(streamingLocatorClient, times(1))
            .create(eq(resourceGroup), eq(accountName), eq(userId), any(StreamingLocatorInner.class));
        verify(streamingEndpointClient, times(1)).get(resourceGroup, accountName, "default");
        verify(streamingEndpointClient, times(1))
            .create(eq(resourceGroup), eq(accountName), eq("default"), any(StreamingEndpointInner.class));
        verify(streamingEndpointClient, times(1)).start(resourceGroup, accountName, "default");
        verify(streamingLocatorClient, times(1)).listPaths(resourceGroup, accountName, userId);
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

    private StreamingPoliciesClient mockStreamingPoliciesClient() {
        var client = mock(StreamingPoliciesClient.class);
        when(amsClient.getStreamingPolicies()).thenReturn(client);
        return client;
    }
}
