package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.models.JobError;
import com.azure.resourcemanager.mediaservices.models.JobInputAsset;
import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.config.JacksonConfiguration;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetStorage;
import uk.gov.hmcts.reform.preapi.media.dto.MkContentKeyPolicy;
import uk.gov.hmcts.reform.preapi.media.dto.MkGetListResponse;
import uk.gov.hmcts.reform.preapi.media.dto.MkJob;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEvent;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveEventProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkLiveOutput;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingEndpoint;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingEndpointProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocator;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorUrlPaths;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingPolicy;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.media.MediaKind.ENCODE_FROM_INGEST_TRANSFORM;
import static uk.gov.hmcts.reform.preapi.media.MediaKind.ENCODE_FROM_MP4_TRANSFORM;

@SpringBootTest(classes = MediaKind.class)
@TestPropertySource(properties = {
    "azure.ingestStorage=testIngestStorageAccount",
})
public class MediaKindTest {
    @MockitoBean
    private MediaKindClient mockClient;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private MediaKindConfiguration mediaKindConfiguration;

    @Autowired
    private MediaKindClient mediaKindClient;

    private MediaKind mediaKind;

    private CaptureSessionDTO captureSession;

    @BeforeEach
    void setUp() {
        captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBookingId(UUID.randomUUID());

        mediaKindConfiguration = new MediaKindConfiguration(
            "Staging",
            "pre-mediakind-stg",
            "testIssuer",
            "testSymmetricKey",
            10,
            10,
            "default",
            "default-live",
            "uksouth"
        );

        mediaKind = new MediaKind(mediaKindConfiguration, mediaKindClient,
                                  azureIngestStorageService, azureFinalStorageService);
    }

    private MkStreamingLocator createStreamingLocator(String userId, int daysOffset) {
        return MkStreamingLocator
            .builder()
            .properties(MkStreamingLocatorProperties
                            .builder()
                            .streamingPolicyName("Predefined_ClearKey")
                            .streamingLocatorId(userId)
                            .endTime(Timestamp.valueOf(OffsetDateTime.now()
                                                                     .plusDays(daysOffset)
                                                                     .toLocalDateTime()))
                            .build()
            )
            .build();
    }

    private MkStreamingLocator getExpiredStreamingLocator(String userId) {
        return createStreamingLocator(userId, -1);
    }

    private MkStreamingLocator getFutureStreamingLocator(String userId) {
        return createStreamingLocator(userId, 1);
    }

    @DisplayName("Should get a list of assets")
    @Test
    void getAssetsSuccess() {
        var asset1 = createMkAsset("asset1");
        var asset2 = createMkAsset("asset2");
        var mockAssets = MkGetListResponse.<MkAsset>builder()
            .value(List.of(asset1, asset2))
            .build();

        when(mockClient.getAssets(eq(0))).thenReturn(mockAssets);

        var assets = mediaKind.getAssets();

        assertThat(assets).hasSize(2);
        assertThat(assets.get(0).getName()).isEqualTo(asset1.getName());
        assertThat(assets.get(1).getName()).isEqualTo(asset2.getName());
    }

    @DisplayName("Should fail to get a list of assets and throw a media kind exception")
    @Test
    void getAssetsFeignExceptionThrown() {
        when(mockClient.getAssets(0)).thenThrow(FeignException.class);

        assertThrows(FeignException.class, () -> mediaKind.getAssets());
    }


    @Test
    void getAllPaginatedMkListEmpty() {
        MediaKind.GetListFunction<String> mockFunction = mock((skip ->
            MkGetListResponse.<String>builder().value(List.of()).build()));

        var result = mediaKind.getAllMkList(mockFunction).toList();

        assertThat(result).isEmpty();
    }

    @Test
    void getAllPaginatedMkListOnePage() {
        MediaKind.GetListFunction<String> mockFunction = mock((skip ->
            MkGetListResponse.<String>builder().value(List.of("item 1", "item 2")).build()));

        var result = mediaKind.getAllMkList(mockFunction).toList();

        assertThat(result).hasSize(2);
        assertThat(result).contains("item 1");
        assertThat(result).contains("item 2");
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

        var result = mediaKind.getAllMkList(mockFunction).toList();

        assertThat(result).hasSize(4);
        assertThat(result).contains("item 1");
        assertThat(result).contains("item 2");
        assertThat(result).contains("item 3");
        assertThat(result).contains("item 4");
    }

    @DisplayName("Should get asset by asset name")
    @Test
    void getAssetByAssetNameSuccess() {
        var assetName = UUID.randomUUID().toString();
        var asset = createMkAsset(assetName);

        when(mockClient.getAsset(eq(assetName))).thenReturn(asset);

        var result = mediaKind.getAsset(assetName);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(assetName);
    }

    @DisplayName("Should throw on MediaKind exception on feign client errors")
    @Test
    void getAssetByAssetNameFeignExceptionThrown() {
        when(mockClient.getAsset(anyString())).thenThrow(FeignException.class);

        assertThrows(
            FeignException.class,
            () -> mediaKind.getAsset("asset1")
        );
        verify(mockClient, times(1)).getAsset(anyString());
    }

    @DisplayName("Should return null when get asset returns 404")
    @Test
    void getAssetByAssetNameNotFound() {
        var mockError = mock(NotFoundException.class);
        when(mockClient.getAsset(anyString())).thenThrow(mockError);

        assertThat(mediaKind.getAsset("asset1")).isNull();

        verify(mockClient, times(1)).getAsset(anyString());
    }

    @DisplayName("Should get live event by name")
    @Test
    void getLiveEventByNameSuccess() {
        var liveEventName = UUID.randomUUID().toString();
        var liveEvent = createLiveEvent(liveEventName);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(liveEvent);

        var result = mediaKind.getLiveEvent(liveEventName);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(liveEventName);
        assertThat(result.getDescription()).isEqualTo(liveEvent.getProperties().getDescription());
        assertThat(result.getResourceState()).isEqualTo(liveEvent.getProperties().getResourceState());
        assertThat(result.getId()).isEqualTo(liveEvent.getId());
        assertThat(result.getInputRtmp())
            .isEqualTo(liveEvent.getProperties().getInput().getEndpoints().getFirst().url());
    }

    @DisplayName("Should throw a NotFoundException null when get live event returns 404")
    @Test
    void getLiveEventNotFound() {
        var mockError = mock(NotFoundException.class);

        when(mockClient.getLiveEvent(anyString())).thenThrow(mockError);
        assertThrows(
            NotFoundException.class,
            () -> mediaKind.getLiveEvent("not-found")
        );
        verify(mockClient, times(1)).getLiveEvent("not-found");
    }

    @DisplayName("Should return a list of all live events")
    @Test
    void getLiveEventListSuccess() {
        var liveEventName1 = UUID.randomUUID().toString();
        var liveEventName2 = UUID.randomUUID().toString();
        var liveEvents = List.of(
            createLiveEvent(liveEventName1),
            createLiveEvent(liveEventName2)
        );

        when(mockClient.getLiveEvents(0))
            .thenReturn(MkGetListResponse.<MkLiveEvent>builder()
                            .value(liveEvents)
                            .build()
            );

        var results = mediaKind.getLiveEvents();
        assertThat(results).hasSize(2);

        assertThat(results.getFirst().getName()).isEqualTo(liveEventName1);
        assertThat(results.getLast().getName()).isEqualTo(liveEventName2);
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

    private MkLiveEvent createLiveEvent(String name) {
        return MkLiveEvent.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .location("UK South")
            .properties(MkLiveEventProperties.builder()
                                             .description("description: " + name)
                                             .useStaticHostname(true)
                                             .resourceState("Stopped")
                                             .input(MkLiveEventProperties.MkLiveEventInput.builder()
                                                    .endpoints(List.of(
                                                        new LiveEventEndpoint()
                                                            .withProtocol("RTMP")
                                                            .withUrl("rtmps://example url")))
                                                        .build())
                                             .preview(new LiveEventPreview())
                                             .build())
            .build();
    }

    @DisplayName("Should return the capture session when successfully started the live event")
    @Test
    void startLiveEventSuccess() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockClient.getStreamingLocator(any()))
            .thenThrow(mock(NotFoundException.class));

        mediaKind.startLiveEvent(captureSession);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(eq(liveEventName), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, times(1)).startLiveEvent(any());
        verify(mockClient, times(1)).getStreamingLocator(any());
        verify(mockClient, times(1)).createStreamingLocator(any(), any());
    }

    @Test
    @DisplayName("Should return the capture session when successfully started the live event")
    void startLiveEventLiveEventConflictSuccess() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.putLiveEvent(any(), any()))
            .thenThrow(mock(ConflictException.class));
        when(mockClient.getStreamingLocator(any()))
            .thenThrow(mock(NotFoundException.class));
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);

        mediaKind.startLiveEvent(captureSession);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, times(1)).startLiveEvent(any());
        verify(mockClient, times(1)).getStreamingLocator(any());
        verify(mockClient, times(1)).createStreamingLocator(any(), any());
    }

    @DisplayName("Should throw not found error when live event cannot be found after creation")
    @Test
    void startLiveEventNotFoundAfterCreate() {
        var liveEventName = captureSession.getId().toString().replace("-", "");

        when(mockClient.getLiveEvent(liveEventName)).thenThrow(mock(NotFoundException.class));

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.startLiveEvent(captureSession)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, never()).createStreamingLocator(any(), any());
    }

    @DisplayName("Should throw 409 error when asset already exists")
    @Test
    void startLiveEventAssetConflict() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockClient.putAsset(eq(liveEventName), any(MkAsset.class)))
            .thenThrow(mock(ConflictException.class));

        var message = assertThrows(
            ConflictException.class,
            () -> mediaKind.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Asset: " + liveEventName);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).deleteLiveEvent(any());
        verify(mockClient, never()).createStreamingLocator(any(), any());
    }

    @DisplayName("Should throw 409 error when live output already exists")
    @Test
    void startLiveEventLiveOutputConflict() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockClient.putLiveOutput(eq(liveEventName), eq(liveEventName), any()))
            .thenThrow(mock(ConflictException.class));

        var message = assertThrows(
            ConflictException.class,
            () -> mediaKind.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Live Output: " + liveEventName);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, never()).createStreamingLocator(any(), any());
    }

    @DisplayName("Should throw 404 error when creating a live output but cannot find live event")
    @Test
    void startLiveEventLiveOutputLiveEventNotFound() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockClient.putLiveOutput(eq(liveEventName), eq(liveEventName), any(MkLiveOutput.class)))
            .thenThrow(NotFoundException.class);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, never()).createStreamingLocator(any(), any());
    }

    @DisplayName("Should throw 404 error when attempting to start live event that cannot be found (after setup)")
    @Test
    void startLiveEventStartNotFound() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        doThrow(mock(NotFoundException.class)).when(mockClient).startLiveEvent(liveEventName);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, times(1)).startLiveEvent(any());
        verify(mockClient, never()).createStreamingLocator(any(), any());
    }

    @Test
    @DisplayName("Should successfully stop live event when there is not a recording found")
    void stopLiveEventAndProcessNoRecording() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();

        when(azureIngestStorageService.doesValidAssetExist(captureSession.getBookingId().toString()))
            .thenReturn(false);

        var res = mediaKind.stopLiveEventAndProcess(captureSession, recordingId);
        assertThat(res).isEqualTo(RecordingStatus.NO_RECORDING);

        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
        verify(azureIngestStorageService, times(1)).doesValidAssetExist(captureSession.getBookingId().toString());
        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete(captureSession.getBookingId().toString());
        verify(mockClient, never()).putAsset(any(), any());
    }

    @Test
    @DisplayName("Should successfully stop live event when there is a recording found")
    void stopLiveEventAndProcessRecordingAvailable() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var tempName = recordingId.toString().replace("-", "");
        var mockJob1 = mock(MkJob.class);
        var mockProperties1 = mock(MkJob.MkJobProperties.class);
        var mockJob2 = mock(MkJob.class);
        var mockProperties2 = mock(MkJob.MkJobProperties.class);

        when(mockClient.getJob(eq(ENCODE_FROM_INGEST_TRANSFORM), startsWith(liveEventName))).thenReturn(mockJob1);
        when(mockClient.getJob(eq(ENCODE_FROM_MP4_TRANSFORM), startsWith(tempName))).thenReturn(mockJob2);
        when(mockJob1.getProperties()).thenReturn(mockProperties1);
        when(mockJob2.getProperties()).thenReturn(mockProperties2);
        when(mockProperties1.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);
        when(mockProperties2.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);

        when(azureIngestStorageService.doesValidAssetExist(captureSession.getBookingId().toString()))
            .thenReturn(true);
        when(azureIngestStorageService.tryGetMp4FileName(recordingId.toString())).thenReturn("index.mp4");
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);

        assertThat(mediaKind.stopLiveEventAndProcess(captureSession, recordingId))
            .isEqualTo(RecordingStatus.RECORDING_AVAILABLE);

        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
        verify(azureIngestStorageService, times(1)).doesValidAssetExist(captureSession.getBookingId().toString());
        verify(mockClient, times(2)).putAsset(any(), any());
        verify(mockClient, times(1)).getTransform(ENCODE_FROM_INGEST_TRANSFORM);
        verify(mockClient, times(1)).getTransform(ENCODE_FROM_MP4_TRANSFORM);
        verify(mockClient, never()).putTransform(any(), any());
        var jobArgument = ArgumentCaptor.forClass(String.class);
        var jobArgument2 = ArgumentCaptor.forClass(String.class);
        var jobArgument3 = ArgumentCaptor.forClass(String.class);
        var jobArgument4 = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).putJob(eq(ENCODE_FROM_INGEST_TRANSFORM), jobArgument.capture(), any(MkJob.class));
        verify(mockClient, times(3)).getJob(eq(ENCODE_FROM_INGEST_TRANSFORM), jobArgument2.capture());
        assertThat(jobArgument.getValue()).startsWith(liveEventName);
        assertThat(jobArgument2.getValue()).startsWith(liveEventName);
        verify(mockClient, times(1)).putJob(eq(ENCODE_FROM_MP4_TRANSFORM), jobArgument3.capture(), any(MkJob.class));
        verify(mockClient, times(3)).getJob(eq(ENCODE_FROM_MP4_TRANSFORM), jobArgument4.capture());
        assertThat(jobArgument3.getValue()).startsWith(tempName);
        assertThat(jobArgument4.getValue()).startsWith(tempName);
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(captureSession.getBookingId().toString());
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(recordingId.toString());
        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete(captureSession.getBookingId().toString());
        verify(azureIngestStorageService, times(1)).markContainerAsSafeToDelete(recordingId.toString());
    }

    @Test
    @DisplayName("Should throw error for stop live event when file cannot be found after first encode job")
    void stopLiveEventAndProcessRecordingEncodingJobNoFileNameFound() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var mockJob1 = mock(MkJob.class);
        var mockProperties1 = mock(MkJob.MkJobProperties.class);

        when(mockClient.getJob(eq(ENCODE_FROM_INGEST_TRANSFORM), startsWith(liveEventName))).thenReturn(mockJob1);

        when(mockJob1.getProperties()).thenReturn(mockProperties1);
        when(mockProperties1.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);

        when(azureIngestStorageService.doesValidAssetExist(captureSession.getBookingId().toString()))
            .thenReturn(true);
        when(azureIngestStorageService.tryGetMp4FileName(recordingId.toString())).thenReturn(null);

        assertThat(mediaKind.stopLiveEventAndProcess(captureSession, recordingId)).isEqualTo(RecordingStatus.FAILURE);

        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
        verify(azureIngestStorageService, times(1)).doesValidAssetExist(captureSession.getBookingId().toString());
        verify(mockClient, times(2)).putAsset(any(), any());
        verify(mockClient, times(1)).getTransform(ENCODE_FROM_INGEST_TRANSFORM);
        verify(mockClient, never()).putTransform(any(), any());
        verify(azureIngestStorageService, never()).doesIsmFileExist(recordingId.toString());
    }

    @Test
    @DisplayName("Should throw error when error occurs creating asset")
    void stopLiveEventAndProcessAssetCreateError() {
        var recordingId = UUID.randomUUID();

        when(azureIngestStorageService.doesValidAssetExist(captureSession.getBookingId().toString()))
            .thenReturn(true);

        when(mockClient.putAsset(any(), any())).thenThrow(FeignException.class);
        assertThrows(
            FeignException.class,
            () -> mediaKind.stopLiveEventAndProcess(captureSession, recordingId)
        );

        verify(mockClient, times(1)).putAsset(any(), any());
    }


    @Test
    @DisplayName("Should create the EncodeFromIngest transform if it doesn't exist")
    void stopLiveEventAndProcessRecordingFoundRunEncodeTransform() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var tempName = recordingId.toString().replace("-", "");
        var mockJob1 = mock(MkJob.class);
        var mockProperties1 = mock(MkJob.MkJobProperties.class);
        var mockJob2 = mock(MkJob.class);
        var mockProperties2 = mock(MkJob.MkJobProperties.class);

        when(mockClient.getTransform(ENCODE_FROM_INGEST_TRANSFORM)).thenThrow(NotFoundException.class);

        when(mockClient.getJob(eq(ENCODE_FROM_INGEST_TRANSFORM), startsWith(liveEventName))).thenReturn(mockJob1);
        when(mockClient.getJob(eq(ENCODE_FROM_MP4_TRANSFORM), startsWith(tempName))).thenReturn(mockJob2);
        when(mockJob1.getProperties()).thenReturn(mockProperties1);
        when(mockJob2.getProperties()).thenReturn(mockProperties2);
        when(mockProperties1.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);
        when(mockProperties2.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);

        when(azureIngestStorageService.doesValidAssetExist(captureSession.getBookingId().toString()))
            .thenReturn(true);
        when(azureIngestStorageService.tryGetMp4FileName(any())).thenReturn("index.mp4");
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);

        assertThat(mediaKind.stopLiveEventAndProcess(captureSession, recordingId))
            .isEqualTo(RecordingStatus.RECORDING_AVAILABLE);

        verify(mockClient, times(2)).putAsset(any(), any());
        verify(mockClient, times(1)).getTransform(ENCODE_FROM_INGEST_TRANSFORM);
        verify(mockClient, times(1)).putTransform(eq(ENCODE_FROM_INGEST_TRANSFORM), any());
        var jobArgument = ArgumentCaptor.forClass(String.class);
        var jobArgument2 = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).putJob(eq(ENCODE_FROM_INGEST_TRANSFORM), jobArgument.capture(), any(MkJob.class));
        verify(mockClient, times(3)).getJob(eq(ENCODE_FROM_INGEST_TRANSFORM), jobArgument2.capture());
        assertThat(jobArgument.getValue()).startsWith(liveEventName);
        assertThat(jobArgument2.getValue()).startsWith(liveEventName);
        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
    }

    @Test
    @DisplayName("Should create the EncodeFromMp4 transform if it doesn't exist")
    void stopLiveEventAndProcessRecordingFoundRunEncodeTransform2() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var tempName = recordingId.toString().replace("-", "");
        var mockJob1 = mock(MkJob.class);
        var mockProperties1 = mock(MkJob.MkJobProperties.class);
        var mockJob2 = mock(MkJob.class);
        var mockProperties2 = mock(MkJob.MkJobProperties.class);

        when(mockClient.getTransform(ENCODE_FROM_INGEST_TRANSFORM)).thenThrow(NotFoundException.class);
        when(mockClient.getTransform(ENCODE_FROM_MP4_TRANSFORM)).thenThrow(NotFoundException.class);

        when(mockClient.getJob(eq(ENCODE_FROM_INGEST_TRANSFORM), startsWith(liveEventName))).thenReturn(mockJob1);
        when(mockClient.getJob(eq(ENCODE_FROM_MP4_TRANSFORM), startsWith(tempName))).thenReturn(mockJob2);
        when(mockJob1.getProperties()).thenReturn(mockProperties1);
        when(mockJob2.getProperties()).thenReturn(mockProperties2);
        when(mockProperties1.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);
        when(mockProperties2.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);

        when(azureIngestStorageService.doesValidAssetExist(captureSession.getBookingId().toString()))
            .thenReturn(true);
        when(azureIngestStorageService.tryGetMp4FileName(any())).thenReturn("index.mp4");
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);

        assertThat(mediaKind.stopLiveEventAndProcess(captureSession, recordingId))
            .isEqualTo(RecordingStatus.RECORDING_AVAILABLE);

        verify(mockClient, times(2)).putAsset(any(), any());
        verify(mockClient, times(1)).getTransform(ENCODE_FROM_INGEST_TRANSFORM);
        verify(mockClient, times(1)).getTransform(ENCODE_FROM_MP4_TRANSFORM);
        verify(mockClient, times(1)).putTransform(eq(ENCODE_FROM_INGEST_TRANSFORM), any());
        verify(mockClient, times(1)).putTransform(eq(ENCODE_FROM_MP4_TRANSFORM), any());
        var jobArgument = ArgumentCaptor.forClass(String.class);
        var jobArgument2 = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).putJob(eq(ENCODE_FROM_INGEST_TRANSFORM), jobArgument.capture(), any(MkJob.class));
        verify(mockClient, times(3)).getJob(eq(ENCODE_FROM_INGEST_TRANSFORM), jobArgument2.capture());
        assertThat(jobArgument.getValue()).startsWith(liveEventName);
        assertThat(jobArgument2.getValue()).startsWith(liveEventName);
        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
    }

    @Test
    @DisplayName("Should throw not found when live event cannot be found to stop")
    void stopLiveEventAndProcessLiveEventNotFound() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();

        doThrow(NotFoundException.class).when(mockClient).stopLiveEvent(any());

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.stopLiveEvent(captureSession, recordingId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, never()).deleteLiveEvent(liveEventName);
    }

    @Test
    @DisplayName("Should successfully stop live event when there is not a streaming endpoint to stop/delete")
    void stopLiveEventAndProcessEndpointNotFound() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();

        when(azureIngestStorageService.doesValidAssetExist(captureSession.getBookingId().toString()))
            .thenReturn(false);
        doThrow(NotFoundException.class).when(mockClient).stopStreamingEndpoint(any());

        var res = mediaKind.stopLiveEventAndProcess(captureSession, recordingId);
        assertThat(res).isEqualTo(RecordingStatus.NO_RECORDING);

        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
        verify(azureIngestStorageService, times(1)).doesValidAssetExist(captureSession.getBookingId().toString());
        verify(mockClient, never()).putAsset(any(), any());
    }

    @DisplayName("Should accept a request to import an asset and return a job response for encoding to mp4")
    @Test
    void importAssetSuccess() throws InterruptedException {

        var newRecordingId = UUID.randomUUID();

        var generateAssetDTO  = new GenerateAssetDTO(newRecordingId + "-input",
                                                     newRecordingId,
                                                     "tmp-asset",
                                                     "final-asset",
                                                     "unit test import asset",
                                                     UUID.randomUUID());

        var mockJob = mock(MkJob.class);
        var mockProperties = mock(MkJob.MkJobProperties.class);
        when(mockClient.getJob(eq(ENCODE_FROM_MP4_TRANSFORM), any())).thenReturn(mockJob);
        when(azureFinalStorageService.getMp4FileName(generateAssetDTO.getSourceContainer())).thenReturn("video.mp4");
        when(mockJob.getProperties()).thenReturn(mockProperties);
        when(mockProperties.getState()).thenReturn(JobState.FINISHED);
        when(mockClient.getTransform(ENCODE_FROM_MP4_TRANSFORM)).thenThrow(NotFoundException.class);

        var result = mediaKind.importAsset(generateAssetDTO, true);

        assertThat(result.getJobStatus()).isEqualTo("Finished");

        var sourceContainerArgument = ArgumentCaptor.forClass(MkAsset.class);

        verify(mockClient, times(1))
            .putAsset(eq(generateAssetDTO.getTempAsset()), sourceContainerArgument.capture());

        assertThat(sourceContainerArgument.getValue().getProperties().getContainer())
            .isEqualTo(generateAssetDTO.getSourceContainer());

        var destinationContainerArgument = ArgumentCaptor.forClass(MkAsset.class);

        verify(mockClient, times(1))
            .putAsset(eq(generateAssetDTO.getFinalAsset()), destinationContainerArgument.capture());

        assertThat(destinationContainerArgument.getValue().getProperties().getContainer())
            .isEqualTo(generateAssetDTO.getDestinationContainer().toString());

        var jobInnerArgument = ArgumentCaptor.forClass(MkJob.class);

        var jobArgument = ArgumentCaptor.forClass(String.class);

        verify(mockClient, times(1))
            .putJob(
                eq(ENCODE_FROM_MP4_TRANSFORM),
                jobArgument.capture(),
                jobInnerArgument.capture()
            );

        assertThat(jobArgument.getValue()).startsWith(generateAssetDTO.getTempAsset());

        assertThat(((JobInputAsset) jobInnerArgument.getValue().getProperties().getInput()).assetName())
            .isEqualTo(generateAssetDTO.getTempAsset());
        assertThat(jobInnerArgument.getValue().getProperties().getOutputs().getFirst().assetName())
            .isEqualTo(generateAssetDTO.getFinalAsset());
    }

    @DisplayName("Should accept a request to import an asset and handle a failed job to encode to mp4")
    @Test
    void importAssetJobFailed() throws InterruptedException {
        var mockJob = mock(MkJob.class);
        var mockProperties = mock(MkJob.MkJobProperties.class);
        var output = mock(JobOutputAsset.class);
        var jobError = mock(JobError.class);
        when(mockClient.getJob(eq(ENCODE_FROM_MP4_TRANSFORM), any())).thenReturn(mockJob);
        when(azureFinalStorageService.getMp4FileName(any())).thenReturn("my-source-container");
        when(mockJob.getProperties()).thenReturn(mockProperties);
        when(mockProperties.getState()).thenReturn(JobState.ERROR);
        when(mockProperties.getOutputs()).thenReturn(List.of(output));
        when(output.error()).thenReturn(jobError);
        when(jobError.message()).thenReturn("something went wrong");

        var newRecordingId = UUID.randomUUID();

        var generateAssetDTO  = new GenerateAssetDTO(newRecordingId + "-input",
                                                     newRecordingId,
                                                     "tmp-asset",
                                                     "final-asset",
                                                     "unit test import asset",
                                                     UUID.randomUUID());

        var result = mediaKind.importAsset(generateAssetDTO, true);

        assertThat(result.getJobStatus()).isEqualTo("Error");

        var sourceContainerArgument = ArgumentCaptor.forClass(MkAsset.class);

        verify(mockClient, times(1))
            .putAsset(eq(generateAssetDTO.getTempAsset()), sourceContainerArgument.capture());

        assertThat(sourceContainerArgument.getValue().getProperties().getContainer())
            .isEqualTo(generateAssetDTO.getSourceContainer());

        var destinationContainerArgument = ArgumentCaptor.forClass(MkAsset.class);

        verify(mockClient, times(1))
            .putAsset(eq(generateAssetDTO.getFinalAsset()), destinationContainerArgument.capture());

        assertThat(destinationContainerArgument.getValue().getProperties().getContainer())
            .isEqualTo(generateAssetDTO.getDestinationContainer().toString());

        var jobName = ArgumentCaptor.forClass(String.class);
        var jobInnerArgument = ArgumentCaptor.forClass(MkJob.class);

        verify(mockClient, times(1))
            .putJob(
                eq(ENCODE_FROM_MP4_TRANSFORM),
                jobName.capture(),
                jobInnerArgument.capture()
            );

        assertThat(jobName.getValue()).startsWith(generateAssetDTO.getTempAsset());
        assertThat(((JobInputAsset) jobInnerArgument.getValue().getProperties().getInput()).assetName())
            .isEqualTo(generateAssetDTO.getTempAsset());
        assertThat(jobInnerArgument.getValue().getProperties().getOutputs().getFirst().assetName())
            .isEqualTo(generateAssetDTO.getFinalAsset());
    }

    @DisplayName("Should fail to play a live event because the live event is not running")
    @Test
    void playLiveEventFailureLiveEventNotRunning() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEventProperties.builder()
                                     .resourceState(LiveEventResourceState.STOPPED.toString())
                                     .build()
            );

        assertThrows(
            LiveEventNotRunningException.class,
            () -> mediaKind.playLiveEvent(captureSession.getId())
        );

    }

    @DisplayName("Should fail to play a live event because the streaming endpoint won't start")
    @Test
    void playLiveEventFailureToStartStreamingEndpoint() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getStreamingEndpointByName("default-live"))
            .thenThrow(new NotFoundException("not found"));
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEventProperties.builder()
                                     .resourceState(LiveEventResourceState.RUNNING.toString())
                                     .build()
            );
        when(mockClient.createStreamingEndpoint(eq("default-live"), any()))
            .thenThrow(new ConflictException("Conflict"));

        assertThrows(
            ConflictException.class,
            () -> mediaKind.playLiveEvent(captureSession.getId())
        );
    }

    @DisplayName("Should play a live event successfully")
    @Test
    void playLiveEventSuccess() throws JsonProcessingException, InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getStreamingEndpointByName("default-live"))
            .thenReturn(MkStreamingEndpoint.builder()
                            .properties(MkStreamingEndpointProperties.builder()
                                            .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                                            .build())
                            .build());
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEventProperties.builder()
                                     .resourceState(LiveEventResourceState.RUNNING.toString())
                                     .build()
            );

        when(mockClient.getStreamingLocator(any()))
            .thenThrow(mock(NotFoundException.class));

        var result = mediaKind.playLiveEvent(captureSession.getId());

        assertThat(result).isEqualTo(
            "https://ep-"
            + "default-live"
            + "-pre-mediakind-stg.uksouth.streaming.mediakind.com/"
            + liveEventName
            + "/index.qfm/manifest(format=m3u8-cmaf)");
    }

    @Test
    @DisplayName("Should play a live event successfully when streaming locator already exists")
    void playLiveEventStreamingLocatorAlreadyExistsSuccess() throws JsonProcessingException, InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getStreamingEndpointByName("default-live"))
            .thenReturn(MkStreamingEndpoint.builder()
                            .properties(MkStreamingEndpointProperties.builder()
                                            .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                                            .build())
                            .build());
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEventProperties.builder()
                    .resourceState(LiveEventResourceState.RUNNING.toString())
                    .build()
            );

        when(mockClient.getStreamingLocator(any()))
            .thenReturn(MkStreamingLocator.builder().build());

        var result = mediaKind.playLiveEvent(captureSession.getId());

        assertThat(result).isEqualTo(
            "https://ep-"
                + "default-live"
                + "-pre-mediakind-stg.uksouth.streaming.mediakind.com/"
                + liveEventName
                + "/index.qfm/manifest(format=m3u8-cmaf)");

        verify(mockClient, never()).createStreamingLocator(any(), any());
    }

    @Test
    @DisplayName("Should throw error when failing to get streaming locator (not 404)")
    void playLiveEventStreamingLocatorFailed() throws JsonProcessingException, InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getStreamingEndpointByName("default-live"))
            .thenReturn(MkStreamingEndpoint.builder()
                            .properties(MkStreamingEndpointProperties.builder()
                                            .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                                            .build())
                            .build());
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEventProperties.builder()
                    .resourceState(LiveEventResourceState.RUNNING.toString())
                    .build()
            );

        when(mockClient.getStreamingLocator(any()))
            .thenThrow(mock(ConflictException.class));

        assertThrows(
            ConflictException.class,
            () -> mediaKind.playLiveEvent(captureSession.getId())
        );
        verify(mockClient, never()).createStreamingLocator(any(), any());
    }

    @SuppressWarnings("checkstyle:Indentation")
    private MkStreamingLocatorUrlPaths getGoodStreamingLocatorPaths(String liveEventName)
        throws JsonProcessingException {
        var jsonSnippet = """
          {
          "streamingPaths": [
            {
              "streamingProtocol": "Dash",
              "encryptionScheme": "NoEncryption",
              "paths": [
                "/%s/index.qfm/manifest(format=mpd-time-cmaf)"
              ]
            },
            {
              "streamingProtocol": "Hls",
              "encryptionScheme": "NoEncryption",
              "paths": [
                "/%s/index.qfm/manifest(format=m3u8-cmaf)"
              ]
            }
          ],
          "downloadPaths": [],
          "drm": {}
        }
        """.formatted(liveEventName, liveEventName);

        var om = new JacksonConfiguration().getMapper();
        return om.readValue(jsonSnippet, MkStreamingLocatorUrlPaths.class);
    }

    @DisplayName("Should return the playback urls for the asset")
    @Test
    void playAssetSuccess() throws InterruptedException {
        var assetName = UUID.randomUUID().toString();
        var userId = UUID.randomUUID().toString();
        var asset = createMkAsset(assetName);
        var streamingEndpoint = MkStreamingEndpoint.builder()
            .properties(
                MkStreamingEndpointProperties.builder()
                    .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                    .hostName("example.com/")
                    .build()
            )
            .build();

        var streamingPaths = MkStreamingLocatorUrlPaths.builder()
            .streamingPaths(List.of(
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.StreamingProtocol.Hls)
                    .build(),
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths
                                           .MkStreamingLocatorStreamingPath
                                           .StreamingProtocol.Dash)
                    .build()
            ))
            .drm(new MkStreamingLocatorUrlPaths
                .MkStreamingLocatorDrm(new MkStreamingLocatorUrlPaths
                    .MkDrmClearKey("license url")))
            .build();

        when(mockClient.getAsset(assetName)).thenReturn(asset);
        when(mockClient.getStreamingEndpointByName("default"))
            .thenReturn(streamingEndpoint);
        when(mockClient.getStreamingLocatorPaths(userId + "_" + assetName)).thenReturn(streamingPaths);

        when(mockClient.getStreamingLocator(userId + "_" + assetName)).thenReturn(getExpiredStreamingLocator(userId));

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).deleteStreamingLocator(userId + "_" + assetName);
        verify(mockClient, times(1))
            .createStreamingLocator(eq(userId + "_" + assetName), any(MkStreamingLocator.class));
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId + "_" + assetName);
    }

    @DisplayName("Should return the playback urls for the asset when content key policy didn't exist")
    @Test
    void playAssetContentKeyNotFoundSuccess() throws InterruptedException, JsonProcessingException {
        var assetName = UUID.randomUUID().toString();
        var userId = UUID.randomUUID().toString();
        var asset = createMkAsset(assetName);
        var streamingEndpoint = MkStreamingEndpoint.builder()
            .properties(
                MkStreamingEndpointProperties.builder()
                    .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                    .hostName("example.com/")
                    .build()
            )
            .build();

        var streamingPaths = MkStreamingLocatorUrlPaths.builder()
            .streamingPaths(List.of(
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.StreamingProtocol.Hls)
                    .build(),
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths
                                           .MkStreamingLocatorStreamingPath
                                           .StreamingProtocol.Dash)
                    .build()
            ))
            .drm(new MkStreamingLocatorUrlPaths
                .MkStreamingLocatorDrm(new MkStreamingLocatorUrlPaths
                .MkDrmClearKey("license url")))
            .build();
        doThrow(NotFoundException.class).when(mockClient).getContentKeyPolicy(userId);
        when(mockClient.getAsset(assetName)).thenReturn(asset);
        when(mockClient.getStreamingEndpointByName("default"))
            .thenReturn(streamingEndpoint);
        when(mockClient.getStreamingLocatorPaths(userId + "_" + assetName)).thenReturn(streamingPaths);

        when(mockClient.getStreamingLocator(userId + "_" + assetName)).thenReturn(getFutureStreamingLocator(userId));

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);

        var mkContentKeyPolicyArgument = ArgumentCaptor.forClass(MkContentKeyPolicy.class);
        verify(mockClient, times(1)).putContentKeyPolicy(eq(userId), mkContentKeyPolicyArgument.capture());
        var om = new JacksonConfiguration().getMapper();

        assertThat(om.writeValueAsString(mkContentKeyPolicyArgument.getValue())).isNotNull();


        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId + "_" + assetName);
    }

    @DisplayName("Should return the playback urls for the asset when streaming policy not found")
    @Test
    void playAssetStreamingPolicyNotFoundSuccess() throws InterruptedException {
        var assetName = UUID.randomUUID().toString();
        var userId = UUID.randomUUID().toString();
        var asset = createMkAsset(assetName);
        var streamingEndpoint = MkStreamingEndpoint.builder()
            .properties(
                MkStreamingEndpointProperties.builder()
                    .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                    .hostName("example.com/")
                    .build()
            )
            .build();

        var streamingPaths = MkStreamingLocatorUrlPaths.builder()
            .streamingPaths(List.of(
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.StreamingProtocol.Hls)
                    .build(),
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths
                                           .MkStreamingLocatorStreamingPath
                                           .StreamingProtocol.Dash)
                    .build()
            ))
            .drm(new MkStreamingLocatorUrlPaths
                .MkStreamingLocatorDrm(new MkStreamingLocatorUrlPaths
                .MkDrmClearKey("license url")))
            .build();

        doThrow(NotFoundException.class).when(mockClient).getStreamingPolicy("Predefined_ClearKey");
        when(mockClient.getAsset(assetName)).thenReturn(asset);
        when(mockClient.getStreamingEndpointByName("default"))
            .thenReturn(streamingEndpoint);
        when(mockClient.getStreamingLocatorPaths(userId + "_" + assetName)).thenReturn(streamingPaths);

        when(mockClient.getStreamingLocator(userId + "_" + assetName)).thenReturn(getExpiredStreamingLocator(userId));

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).deleteStreamingLocator(userId + "_" + assetName);
        verify(mockClient, times(1)).putStreamingPolicy(eq("Predefined_ClearKey"), any(MkStreamingPolicy.class));
        verify(mockClient, times(1))
            .createStreamingLocator(eq(userId + "_" + assetName), any(MkStreamingLocator.class));
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId + "_" + assetName);
    }

    @DisplayName("Should return the playback urls for the asset when default endpoint not running")
    @Test
    void playAssetStreamingEndpointNotRunningSuccess() throws InterruptedException {
        var assetName = UUID.randomUUID().toString();
        var userId = UUID.randomUUID().toString();
        var asset = createMkAsset(assetName);
        var mockProperties = mock(MkStreamingEndpointProperties.class);

        when(mockProperties.getHostName()).thenReturn("example.com/");
        when(mockProperties.getResourceState()).thenReturn(
            MkStreamingEndpointProperties.ResourceState.Stopped,
            MkStreamingEndpointProperties.ResourceState.Starting,
            MkStreamingEndpointProperties.ResourceState.Starting,
            MkStreamingEndpointProperties.ResourceState.Running
        );

        var streamingEndpoint = MkStreamingEndpoint.builder()
            .name("default")
            .properties(mockProperties)
            .build();

        var streamingPaths = MkStreamingLocatorUrlPaths.builder()
            .streamingPaths(List.of(
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.StreamingProtocol.Hls)
                    .build(),
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths
                                           .MkStreamingLocatorStreamingPath
                                           .StreamingProtocol.Dash)
                    .build()
            ))
            .drm(new MkStreamingLocatorUrlPaths
                .MkStreamingLocatorDrm(new MkStreamingLocatorUrlPaths
                .MkDrmClearKey("license url")))
            .build();

        when(mockClient.getAsset(assetName)).thenReturn(asset);
        when(mockClient.getStreamingEndpointByName("default"))
            .thenReturn(streamingEndpoint);
        when(mockClient.getStreamingLocatorPaths(userId + "_" + assetName)).thenReturn(streamingPaths);

        when(mockClient.getStreamingLocator(userId + "_" + assetName)).thenReturn(getExpiredStreamingLocator(userId));

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).deleteStreamingLocator(userId + "_" + assetName);
        verify(mockClient, times(1))
            .createStreamingLocator(eq(userId + "_" + assetName), any(MkStreamingLocator.class));
        verify(mockClient, times(3)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId + "_" + assetName);
    }

    @DisplayName("Should return the playback urls for the asset when default endpoint not created")
    @Test
    void playAssetStreamingEndpointNotFoundSuccess() throws InterruptedException {
        var assetName = UUID.randomUUID().toString();
        var userId = UUID.randomUUID().toString();
        var asset = createMkAsset(assetName);
        var streamingEndpoint = MkStreamingEndpoint.builder()
            .name("default")
            .properties(
                MkStreamingEndpointProperties.builder()
                    .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                    .hostName("example.com/")
                    .build()
            )
            .build();

        var streamingPaths = MkStreamingLocatorUrlPaths.builder()
            .streamingPaths(List.of(
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.StreamingProtocol.Hls)
                    .build(),
                MkStreamingLocatorUrlPaths.MkStreamingLocatorStreamingPath.builder()
                    .encryptionScheme(MkStreamingLocatorUrlPaths
                                          .MkStreamingLocatorStreamingPath
                                          .EncryptionScheme.EnvelopeEncryption)
                    .paths(List.of("playback/" + assetName))
                    .streamingProtocol(MkStreamingLocatorUrlPaths
                                           .MkStreamingLocatorStreamingPath
                                           .StreamingProtocol.Dash)
                    .build()
            ))
            .drm(new MkStreamingLocatorUrlPaths
                .MkStreamingLocatorDrm(new MkStreamingLocatorUrlPaths
                .MkDrmClearKey("license url")))
            .build();

        when(mockClient.getAsset(assetName)).thenReturn(asset);

        when(mockClient.getStreamingEndpointByName("default"))
            .thenThrow(NotFoundException.class)
            .thenReturn(streamingEndpoint);
        when(mockClient.createStreamingEndpoint(eq("default"), any(MkStreamingEndpoint.class)))
            .thenReturn(streamingEndpoint);
        when(mockClient.getStreamingLocatorPaths(userId + "_" + assetName)).thenReturn(streamingPaths);

        when(mockClient.getStreamingLocator(userId + "_" + assetName)).thenReturn(getExpiredStreamingLocator(userId));

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).deleteStreamingLocator(userId + "_" + assetName);
        verify(mockClient, times(1))
            .createStreamingLocator(eq(userId + "_" + assetName), any(MkStreamingLocator.class));
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).createStreamingEndpoint(eq("default"), any(MkStreamingEndpoint.class));
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId + "_" + assetName);
    }

    @DisplayName("Should throw not found error when both dash and hls are null")
    @Test
    void playAssetNotFound() throws InterruptedException {
        var assetName = UUID.randomUUID().toString();
        var userId = UUID.randomUUID().toString();
        var asset = createMkAsset(assetName);
        var streamingEndpoint = MkStreamingEndpoint.builder()
            .properties(
                MkStreamingEndpointProperties.builder()
                    .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                    .hostName("example.com/")
                    .build()
            )
            .build();

        var streamingPaths = MkStreamingLocatorUrlPaths.builder()
            .streamingPaths(List.of())
            .drm(new MkStreamingLocatorUrlPaths
                .MkStreamingLocatorDrm(new MkStreamingLocatorUrlPaths
                .MkDrmClearKey("license url")))
            .build();

        when(mockClient.getAsset(assetName)).thenReturn(asset);
        when(mockClient.getStreamingEndpointByName("default"))
            .thenReturn(streamingEndpoint);
        when(mockClient.getStreamingLocatorPaths(userId + "_" + assetName)).thenReturn(streamingPaths);

        when(mockClient.getStreamingLocator(userId + "_" + assetName)).thenThrow(NotFoundException.class);

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.playAsset(assetName, userId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Playback URL");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1))
            .createStreamingLocator(eq(userId + "_" + assetName), any(MkStreamingLocator.class));
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId + "_" + assetName);
    }

    @DisplayName("Should Delete all streaming locators and Content Key Policies")
    @Test
    void testDeleteAllStreamingLocatorsAndContentKeyPolicies() {

        var locators = List.of(
            MkStreamingLocator.builder().name("locator1").build(),
            MkStreamingLocator.builder().name("locator2").build(),
            MkStreamingLocator.builder().name("locator3").build()
        );

        var contentKeyPolicies = List.of(
            MkContentKeyPolicy.builder().name("policy1").build(),
            MkContentKeyPolicy.builder().name("policy2").build(),
            MkContentKeyPolicy.builder().name("policy3").build()
        );

        when(mockClient.getStreamingLocators(0)).thenReturn(MkGetListResponse.<MkStreamingLocator>builder()
                                                                                 .value(locators)
                                                                                 .build()
        );

        // prove it continues to delete remaining locators even if one fails
        doThrow(new RuntimeException("An error"))
            .when(mockClient).deleteStreamingLocator("locator2");


        when(mockClient.getContentKeyPolicies(0)).thenReturn(MkGetListResponse.<MkContentKeyPolicy>builder()
                                                                                  .value(contentKeyPolicies)
                                                                                  .build());

        // prove it continues to delete remaining policies even if one fails
        doThrow(new RuntimeException("An error"))
            .when(mockClient).deleteContentKeyPolicy("policy2");

        mediaKind.deleteAllStreamingLocatorsAndContentKeyPolicies();

        verify(mockClient, times(1)).getStreamingLocators(anyInt());
        verify(mockClient, times(3)).deleteStreamingLocator(anyString());
        verify(mockClient, times(1)).getContentKeyPolicies(anyInt());
        verify(mockClient, times(3)).deleteContentKeyPolicy(anyString());
    }

    @Test
    @DisplayName("Should not error when stopping live event when live output has not been deleted")
    void cleanupStoppedLiveEventLiveOutputNotDeletedSuccess() {
        var liveEventId = UUID.randomUUID().toString().replace("-", "");

        doThrow(FeignException.BadRequest.class).when(mockClient).stopLiveEvent(liveEventId);

        mediaKind.cleanupStoppedLiveEvent(liveEventId);

        verify(mockClient, times(1)).deleteLiveOutput(liveEventId, liveEventId);
        verify(mockClient, times(1)).stopLiveEvent(liveEventId);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventId);
    }

    @Test
    @DisplayName("Should stop live event by id")
    void stopLiveEvent() {
        var liveEventName = "liveEventName";

        mediaKind.stopLiveEvent(liveEventName);

        verify(mediaKindClient, times(1)).stopLiveEvent(liveEventName);
        verify(mediaKindClient, times(1)).deleteLiveEvent(liveEventName);
    }

    @Test
    @DisplayName("Should stop live event by id, or do nothing if id doesn't exist")
    void stopLiveEventOnError() {
        var liveEventName = "liveEventName";
        doThrow(NotFoundException.class).when(mockClient).stopLiveEvent(liveEventName);

        mediaKind.stopLiveEvent(liveEventName);

        verify(mediaKindClient, times(1)).stopLiveEvent(liveEventName);
        verify(mediaKindClient, never()).deleteLiveEvent(liveEventName);
    }

    @Test
    @DisplayName("Should return recording available when job has completed")
    void hasJobCompletedOnMkFinished() {
        var mkJob = MkJob.builder()
            .properties(MkJob.MkJobProperties.builder()
                            .state(JobState.FINISHED)
                            .build())
            .build();
        when(mockClient.getJob("transform1", "job1")).thenReturn(mkJob);

        var status = mediaKind.hasJobCompleted("transform1", "job1");
        assertThat(status).isEqualTo(RecordingStatus.RECORDING_AVAILABLE);
    }

    @Test
    @DisplayName("Should return failure on MediaKind job error")
    void hasJobCompletedOnMkError() {
        var jobOutputAsset = mock(JobOutputAsset.class);
        var jobError = mock(JobError.class);
        when(jobError.message()).thenReturn("error message");
        when(jobOutputAsset.error()).thenReturn(jobError);
        var mkJob = MkJob.builder()
            .properties(MkJob.MkJobProperties.builder()
                            .state(JobState.ERROR)
                            .outputs(List.of(jobOutputAsset))
                            .build())
            .build();
        when(mockClient.getJob("transform1", "job1")).thenReturn(mkJob);

        var status = mediaKind.hasJobCompleted("transform1", "job1");
        assertThat(status).isEqualTo(RecordingStatus.FAILURE);
    }

    @Test
    @DisplayName("Should return failure on MediaKind job cancelled")
    void hasJobCompletedOnMkCancelled() {
        var mkJob = MkJob.builder()
            .properties(MkJob.MkJobProperties.builder()
                            .state(JobState.CANCELED)
                            .build())
            .build();
        when(mockClient.getJob("transform1", "job1")).thenReturn(mkJob);

        var status = mediaKind.hasJobCompleted("transform1", "job1");
        assertThat(status).isEqualTo(RecordingStatus.FAILURE);
    }

    @Test
    @DisplayName("Should return processing on MediaKind job if not finished, cancelled or errored")
    void hasJobCompletedOnMkProcessing() {
        var mkJob = MkJob.builder()
            .properties(MkJob.MkJobProperties.builder()
                            .state(JobState.PROCESSING)
                            .build())
            .build();
        when(mockClient.getJob("transform1", "job1")).thenReturn(mkJob);

        var status = mediaKind.hasJobCompleted("transform1", "job1");
        assertThat(status).isEqualTo(RecordingStatus.PROCESSING);
    }

    @Test
    @DisplayName("Should return true when asset is created in MediaKind when importing final asset")
    void importAssetFinalAssetSuccess() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());

        boolean result = mediaKind.importAsset(recording, true);

        assertThat(result).isTrue();

        ArgumentCaptor<String> assetNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MkAsset> mkAssetCaptor = ArgumentCaptor.forClass(MkAsset.class);
        verify(mediaKindClient, times(1)).putAsset(assetNameCaptor.capture(), mkAssetCaptor.capture());

        assertThat(assetNameCaptor.getValue())
            .isEqualTo(recording.getId().toString().replace("-", "") + "_output");
        assertThat(mkAssetCaptor.getValue().getProperties().getContainer())
            .isEqualTo(recording.getId().toString());
    }

    @Test
    @DisplayName("Should return true when asset is created in MediaKind when importing ingest asset")
    void importAssetIngestAssetSuccess() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());

        boolean result = mediaKind.importAsset(recording, false);

        assertThat(result).isTrue();

        ArgumentCaptor<String> assetNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MkAsset> mkAssetCaptor = ArgumentCaptor.forClass(MkAsset.class);
        verify(mediaKindClient, times(1)).putAsset(assetNameCaptor.capture(), mkAssetCaptor.capture());

        assertThat(assetNameCaptor.getValue())
            .isEqualTo(recording.getId().toString().replace("-", "") + "_temp");
        assertThat(mkAssetCaptor.getValue().getProperties().getContainer())
            .isEqualTo(recording.getId().toString() + "-input");
    }

    @Test
    @DisplayName("Should return false when asset fails to be created in MediaKind when importing final asset")
    void importAssetFinalAssetFailure() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());

        doThrow(ConflictException.class).when(mockClient).putAsset(any(), any());

        boolean result = mediaKind.importAsset(recording, true);

        assertThat(result).isFalse();

        ArgumentCaptor<String> assetNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MkAsset> mkAssetCaptor = ArgumentCaptor.forClass(MkAsset.class);
        verify(mediaKindClient, times(1)).putAsset(assetNameCaptor.capture(), mkAssetCaptor.capture());

        assertThat(assetNameCaptor.getValue())
            .isEqualTo(recording.getId().toString().replace("-", "") + "_output");
        assertThat(mkAssetCaptor.getValue().getProperties().getContainer())
            .isEqualTo(recording.getId().toString());
    }

    @Test
    @DisplayName("Should not detect live feed when no subperiods are present in the asset tracks")
    void playLiveEventNoPeriodsSuccess() throws JsonProcessingException, InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getStreamingEndpointByName("default-live"))
            .thenReturn(MkStreamingEndpoint.builder()
                            .properties(MkStreamingEndpointProperties.builder()
                                            .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                                            .build())
                            .build());
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEventProperties.builder()
                    .resourceState(LiveEventResourceState.RUNNING.toString())
                    .build()
            );

        var mockAssetStorage = mock(MkAssetStorage.class);
        when(mockClient.getAssetTracks(liveEventName)).thenReturn(mockAssetStorage);
        when(mockAssetStorage.getSpec())
            .thenReturn(
                MkAssetStorage.MkAssetStorageSpec.builder()
                    .periods(null)
                    .build()
            );

        when(mockClient.getStreamingLocator(any()))
            .thenReturn(MkStreamingLocator.builder().build());

        var result = mediaKind.checkLiveFeedAvailable(captureSession.getId());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should detect live feed when subperiods are present in the asset tracks")
    void playLiveEventWithPeriodsSuccess() throws JsonProcessingException, InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getStreamingEndpointByName("default-live"))
            .thenReturn(MkStreamingEndpoint.builder()
                            .properties(MkStreamingEndpointProperties.builder()
                                            .resourceState(MkStreamingEndpointProperties.ResourceState.Running)
                                            .build())
                            .build());
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEventProperties.builder()
                    .resourceState(LiveEventResourceState.RUNNING.toString())
                    .build()
            );

        var mockAssetStorage = mock(MkAssetStorage.class);
        when(mockClient.getAssetTracks(liveEventName)).thenReturn(mockAssetStorage);

        var examplePeriods = JsonNodeFactory.instance.objectNode();
        examplePeriods.set("0/index/edge", JsonNodeFactory.instance.objectNode());
        when(mockAssetStorage.getSpec())
            .thenReturn(
                MkAssetStorage.MkAssetStorageSpec.builder()
                    .periods(examplePeriods)
                    .build()
            );

        when(mockClient.getStreamingLocator(any()))
            .thenReturn(MkStreamingLocator.builder().build());

        var result = mediaKind.checkLiveFeedAvailable(captureSession.getId());

        assertThat(result).isTrue();
    }
}
