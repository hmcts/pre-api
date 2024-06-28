package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AssetsClient;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.JobsClient;
import com.azure.resourcemanager.mediaservices.fluent.LiveEventsClient;
import com.azure.resourcemanager.mediaservices.fluent.LiveOutputsClient;
import com.azure.resourcemanager.mediaservices.fluent.StreamingEndpointsClient;
import com.azure.resourcemanager.mediaservices.fluent.StreamingLocatorsClient;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.JobInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveOutputInner;
import com.azure.resourcemanager.mediaservices.models.JobState;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
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

    @MockBean
    private AzureFinalStorageService azureFinalStorageService;

    private final String resourceGroup = "test-resource-group";
    private final String accountName = "test-account-name";
    private final String ingestSa = "test-ingest-sa";
    private final String finalSa = "test-final-sa";
    private final String env = "test-env";

    private AzureMediaService mediaService;

    private CaptureSessionDTO captureSession;

    @BeforeEach
    void setUp() {
        mediaService = new AzureMediaService(
            resourceGroup,
            accountName,
            ingestSa,
            finalSa,
            env,
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

    @DisplayName("Should successfully stop live event when there is not a recording found")
    @Test
    void stopLiveEventNoRecording() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();
        var streamingLocatorClient = mockStreamingLocatorClient();
        var liveOutputClient = mockLiveOutputClient();

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.PROCESSING, JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);

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

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.PROCESSING, JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);

        assertThat(mediaService.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.RECORDING_AVAILABLE);

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

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
            .thenReturn(mockJob);
        when(mockJob.state()).thenReturn(JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);
        var e = mockAmsError(404);
        doThrow(e).when(liveEventClient).stop(any(), any(), any(), any());

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaService.stopLiveEvent(captureSession, recordingId)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

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

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
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

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
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

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
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

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
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
    void stopLiveEventStreamingLocatorError()  {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var assetsClient = mockAssetsClient();
        var jobsClient = mockJobsClient();
        var mockJob = mock(JobInner.class);
        var liveEventClient = mockLiveEventClient();
        var streamingEndpointClient = mockStreamingEndpointClient();
        var streamingLocatorClient = mockStreamingLocatorClient();

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
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

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
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

        when(jobsClient.get(resourceGroup, accountName, "EncodeToMP4", liveEventName))
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
}
