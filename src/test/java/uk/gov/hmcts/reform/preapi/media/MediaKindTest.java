package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.models.JobState;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import com.fasterxml.jackson.core.JsonProcessingException;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.preapi.config.JacksonConfiguration;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.LiveEventNotRunningException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.dto.MkAsset;
import uk.gov.hmcts.reform.preapi.media.dto.MkAssetProperties;
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

@SpringBootTest(classes = MediaKind.class)
@TestPropertySource(properties = {
    "azure.ingestStorage=testIngestStorageAccount",
    "platform-env=Staging",
    "mediakind.subscription=pre-mediakind-stg",
    "mediakind.issuer=testIssuer",
    "mediakind.symmetricKey=testSymmetricKey"
})
public class MediaKindTest {
    @MockBean
    private MediaKindClient mockClient;

    @MockBean
    private AzureFinalStorageService azureFinalStorageService;

    @Autowired
    private MediaKind mediaKind;

    private CaptureSessionDTO captureSession;

    private static final String ENCODE_TO_MP4 = "EncodeToMp4";

    @BeforeEach
    void setUp() {
        captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBookingId(UUID.randomUUID());
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
        assertThat(result.getInputRtmp()).isEqualTo(liveEvent.getProperties().getInput().endpoints().getFirst().url());
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
                                             .input(new LiveEventInput()
                                       .withEndpoints(List.of(new LiveEventEndpoint()
                                                                  .withProtocol("RTMP")
                                                                  .withUrl("rtmps://example url"))))
                                             .preview(new LiveEventPreview())
                                             .build())
            .build();
    }

    private MkStreamingLocator createMkStreamingLocator(String name) {
        return MkStreamingLocator.builder()
            .name(name)
            .properties(MkStreamingLocatorProperties.builder()
                            .assetName("asset name")
                            .streamingPolicyName("streaming policy name")
                            .streamingLocatorId("streaming locator id")
                            .build())
            .build();
    }

    @DisplayName("Should return the capture session when successfully started the live event")
    @Test
    void startLiveEventSuccess() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);

        mediaKind.startLiveEvent(captureSession);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, times(1)).startLiveEvent(any());
    }

    @DisplayName("Should return the capture session when successfully started the live event")
    @Test
    void startLiveEventLiveEventConflictSuccess() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.putLiveEvent(any(), any()))
            .thenThrow(mock(FeignException.Conflict.class));
        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);

        mediaKind.startLiveEvent(captureSession);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
        verify(mockClient, times(1)).startLiveEvent(any());
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
    }

    @DisplayName("Should throw 409 error when asset already exists")
    @Test
    void startLiveEventAssetConflict() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockClient.putAsset(eq(liveEventName), any(MkAsset.class)))
            .thenThrow(mock(FeignException.Conflict.class));

        var message = assertThrows(
            ConflictException.class,
            () -> mediaKind.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Asset: " + liveEventName);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
    }

    @DisplayName("Should throw 409 error when live output already exists")
    @Test
    void startLiveEventLiveOutputConflict() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockClient.putLiveOutput(eq(liveEventName), eq(liveEventName), any()))
            .thenThrow(mock(FeignException.Conflict.class));

        var message = assertThrows(
            ConflictException.class,
            () -> mediaKind.startLiveEvent(captureSession)
        ).getMessage();
        assertThat(message).isEqualTo("Conflict: Live Output: " + liveEventName);

        verify(mockClient, times(1)).putLiveEvent(any(), any());
        verify(mockClient, times(1)).getLiveEvent(any());
        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).putLiveOutput(any(), any(), any());
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
    }

    @DisplayName("Should successfully stop live event when there is not a recording found")
    @Test
    void stopLiveEventNoRecording() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var mockJob = mock(MkJob.class);
        var mockProperties = mock(MkJob.MkJobProperties.class);

        when(mockClient.getJob(eq(ENCODE_TO_MP4), startsWith(liveEventName))).thenReturn(mockJob);
        when(mockJob.getProperties()).thenReturn(mockProperties);
        when(mockProperties.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.ERROR);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);

        assertThat(mediaKind.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.NO_RECORDING);

        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).getTransform(ENCODE_TO_MP4);
        var jobArgument = ArgumentCaptor.forClass(String.class);
        var jobArgument2 = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).putJob(eq(ENCODE_TO_MP4), jobArgument.capture(), any(MkJob.class));
        verify(mockClient, times(2)).getJob(eq(ENCODE_TO_MP4), jobArgument2.capture());
        assertThat(jobArgument.getValue()).startsWith(liveEventName);
        assertThat(jobArgument2.getValue()).startsWith(liveEventName);
        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).stopStreamingEndpoint(any());
        verify(mockClient, times(1)).deleteStreamingEndpoint(any());
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
    }

    @DisplayName("Should successfully stop live event when there is a recording found")
    @Test
    void stopLiveEventRecordingFound() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var mockJob = mock(MkJob.class);
        var mockProperties = mock(MkJob.MkJobProperties.class);

        when(mockClient.getJob(eq(ENCODE_TO_MP4), startsWith(liveEventName))).thenReturn(mockJob);
        when(mockJob.getProperties()).thenReturn(mockProperties);
        when(mockProperties.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);

        assertThat(mediaKind.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.RECORDING_AVAILABLE);

        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).getTransform(ENCODE_TO_MP4);
        verify(mockClient, never()).putTransform(any(), any());
        var jobArgument = ArgumentCaptor.forClass(String.class);
        var jobArgument2 = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).putJob(eq(ENCODE_TO_MP4), jobArgument.capture(), any(MkJob.class));
        verify(mockClient, times(2)).getJob(eq(ENCODE_TO_MP4), jobArgument2.capture());
        assertThat(jobArgument.getValue()).startsWith(liveEventName);
        assertThat(jobArgument2.getValue()).startsWith(liveEventName);
        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).stopStreamingEndpoint(any());
        verify(mockClient, times(1)).deleteStreamingEndpoint(any());
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
    }

    @DisplayName("Should throw error when error occurs creating asset")
    @Test
    void stopLiveEventAssetCreateError() {
        var recordingId = UUID.randomUUID();

        when(mockClient.putAsset(any(), any())).thenThrow(FeignException.class);
        assertThrows(
            FeignException.class,
            () -> mediaKind.stopLiveEvent(captureSession, recordingId)
        );

        verify(mockClient, times(1)).putAsset(any(), any());
    }

    @DisplayName("Should create the EncodeToMp4 transform if it doesn't exist")
    @Test
    void stopLiveEventRecordingFoundEncodeToMp4() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var mockJob = mock(MkJob.class);
        var mockProperties = mock(MkJob.MkJobProperties.class);

        when(mockClient.getTransform(ENCODE_TO_MP4)).thenThrow(NotFoundException.class);
        when(mockClient.getJob(eq(ENCODE_TO_MP4), startsWith(liveEventName))).thenReturn(mockJob);
        when(mockJob.getProperties()).thenReturn(mockProperties);
        when(mockProperties.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);

        assertThat(mediaKind.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.RECORDING_AVAILABLE);

        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).getTransform(ENCODE_TO_MP4);
        verify(mockClient, times(1)).putTransform(eq(ENCODE_TO_MP4), any());
        var jobArgument = ArgumentCaptor.forClass(String.class);
        var jobArgument2 = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).putJob(eq(ENCODE_TO_MP4), jobArgument.capture(), any(MkJob.class));
        verify(mockClient, times(2)).getJob(eq(ENCODE_TO_MP4), jobArgument2.capture());
        assertThat(jobArgument.getValue()).startsWith(liveEventName);
        assertThat(jobArgument2.getValue()).startsWith(liveEventName);
        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
    }

    @DisplayName("Should throw not found when live event cannot be found to stop")
    @Test
    void stopLiveEventLiveEventNotFound() {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var mockJob = mock(MkJob.class);
        var mockProperties = mock(MkJob.MkJobProperties.class);

        when(mockClient.getJob(eq(ENCODE_TO_MP4), startsWith(liveEventName))).thenReturn(mockJob);
        when(mockJob.getProperties()).thenReturn(mockProperties);
        when(mockProperties.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.FINISHED);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(true);

        doThrow(NotFoundException.class).when(mockClient).stopLiveEvent(any());

        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.stopLiveEvent(captureSession, recordingId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Live Event: " + liveEventName);

        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).getTransform(ENCODE_TO_MP4);
        verify(mockClient, never()).putTransform(any(), any());
        var jobName = ArgumentCaptor.forClass(String.class);
        var jobName2 = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).putJob(eq(ENCODE_TO_MP4), jobName.capture(), any(MkJob.class));
        verify(mockClient, times(2)).getJob(eq(ENCODE_TO_MP4), jobName2.capture());
        assertThat(jobName.getValue()).startsWith(liveEventName);
        assertThat(jobName.getValue()).startsWith(liveEventName);
        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, never()).deleteLiveEvent(liveEventName);
    }

    @DisplayName("Should successfully stop live event when there is not a streaming endpoint to stop/delete")
    @Test
    void stopLiveEventEndpointNotFound() throws InterruptedException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var recordingId = UUID.randomUUID();
        var mockJob = mock(MkJob.class);
        var mockProperties = mock(MkJob.MkJobProperties.class);

        when(mockClient.getJob(eq(ENCODE_TO_MP4), startsWith(liveEventName))).thenReturn(mockJob);
        when(mockJob.getProperties()).thenReturn(mockProperties);
        when(mockProperties.getState()).thenReturn(JobState.PROCESSING, JobState.PROCESSING, JobState.ERROR);
        when(azureFinalStorageService.doesIsmFileExist(recordingId.toString())).thenReturn(false);
        doThrow(NotFoundException.class).when(mockClient).stopStreamingEndpoint(any());

        assertThat(mediaKind.stopLiveEvent(captureSession, recordingId))
            .isEqualTo(RecordingStatus.NO_RECORDING);

        verify(mockClient, times(1)).putAsset(any(), any());
        verify(mockClient, times(1)).getTransform(ENCODE_TO_MP4);
        var jobArgument = ArgumentCaptor.forClass(String.class);
        var jobArgument2 = ArgumentCaptor.forClass(String.class);
        verify(mockClient, times(1)).putJob(eq(ENCODE_TO_MP4), jobArgument.capture(), any(MkJob.class));
        verify(mockClient, times(2)).getJob(eq(ENCODE_TO_MP4), jobArgument2.capture());
        assertThat(jobArgument.getValue()).startsWith(liveEventName);
        assertThat(jobArgument2.getValue()).startsWith(liveEventName);
        verify(mockClient, times(1)).stopLiveEvent(liveEventName);
        verify(mockClient, times(1)).deleteLiveEvent(liveEventName);
        verify(mockClient, times(1)).stopStreamingEndpoint(any());
        verify(mockClient, never()).deleteStreamingEndpoint(any());
        verify(mockClient, times(1)).deleteStreamingLocator(any());
        verify(mockClient, times(1)).deleteLiveOutput(liveEventName, liveEventName);
    }

    @DisplayName("Should accept a request to import an asset and return a job response for encoding to mp4")
    @Test
    void importAssetSuccess() throws InterruptedException {
        var mockJob = mock(MkJob.class);
        var mockProperties = mock(MkJob.MkJobProperties.class);
        when(mockClient.getJob(eq(ENCODE_TO_MP4), any())).thenReturn(mockJob);
        when(mockJob.getProperties()).thenReturn(mockProperties);
        when(mockProperties.getState()).thenReturn(JobState.FINISHED);

        var generateAssetDTO  = new GenerateAssetDTO("my-source-container",
                                                     "my-destination-container",
                                                     "tmp-asset",
                                                     "final-asset",
                                                     "unit test import asset");

        var result = mediaKind.importAsset(generateAssetDTO);

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
            .isEqualTo(generateAssetDTO.getDestinationContainer());

        var jobInnerArgument = ArgumentCaptor.forClass(MkJob.class);

        var jobArgument = ArgumentCaptor.forClass(String.class);

        verify(mockClient, times(1))
            .putJob(
                eq("EncodeToMp4"),
                jobArgument.capture(),
                jobInnerArgument.capture()
            );

        assertThat(jobArgument.getValue()).startsWith(generateAssetDTO.getTempAsset());

        assertThat(jobInnerArgument.getValue().getProperties().getInput().assetName())
            .isEqualTo(generateAssetDTO.getTempAsset());
        assertThat(jobInnerArgument.getValue().getProperties().getOutputs().getFirst().assetName())
            .isEqualTo(generateAssetDTO.getFinalAsset());
    }

    @DisplayName("Should accept a request to import an asset and handle a failed job to encode to mp4")
    @Test
    void importAssetJobFailed() throws InterruptedException {
        var mockJob = mock(MkJob.class);
        var mockProperties = mock(MkJob.MkJobProperties.class);
        when(mockClient.getJob(eq(ENCODE_TO_MP4), any())).thenReturn(mockJob);
        when(mockJob.getProperties()).thenReturn(mockProperties);
        when(mockProperties.getState()).thenReturn(JobState.ERROR);

        var generateAssetDTO  = new GenerateAssetDTO("my-source-container",
                                                     "my-destination-container",
                                                     "tmp-asset",
                                                     "final-asset",
                                                     "unit test import asset");

        var result = mediaKind.importAsset(generateAssetDTO);

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
            .isEqualTo(generateAssetDTO.getDestinationContainer());

        var jobName = ArgumentCaptor.forClass(String.class);
        var jobInnerArgument = ArgumentCaptor.forClass(MkJob.class);

        verify(mockClient, times(1))
            .putJob(
                eq("EncodeToMp4"),
                jobName.capture(),
                jobInnerArgument.capture()
            );

        assertThat(jobName.getValue()).startsWith(generateAssetDTO.getTempAsset());
        assertThat(jobInnerArgument.getValue().getProperties().getInput().assetName())
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

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEventProperties.builder()
                                     .resourceState(LiveEventResourceState.RUNNING.toString())
                                     .build()
            );
        var endpointName = liveEventName.substring(0, 23);
        when(mockClient.createStreamingEndpoint(eq(endpointName), any()))
            .thenThrow(new ConflictException("Conflict"));

        doThrow(mock(FeignException.InternalServerError.class))
            .when(mockClient).startStreamingEndpoint(endpointName);

        assertThrows(
            FeignException.class,
            () -> mediaKind.playLiveEvent(captureSession.getId())
        );
    }

    @DisplayName("Should play a live event successfully")
    @Test
    @SuppressWarnings("checkstyle:linelength")
    void playLiveEventSuccess() throws JsonProcessingException {
        var liveEventName = captureSession.getId().toString().replace("-", "");
        var mockLiveEvent = mock(MkLiveEvent.class);

        when(mockClient.getLiveEvent(liveEventName)).thenReturn(mockLiveEvent);
        when(mockLiveEvent.getProperties())
            .thenReturn(
                MkLiveEventProperties.builder()
                                     .resourceState(LiveEventResourceState.RUNNING.toString())
                                     .build()
            );

        when(mockClient.createStreamingLocator(eq(liveEventName), any()))
            .thenThrow(new ConflictException("Conflict"));

        when(mockClient.listStreamingLocatorPaths(liveEventName))
            .thenReturn(getGoodStreamingLocatorPaths(liveEventName));

        var result = mediaKind.playLiveEvent(captureSession.getId());

        assertThat(result).isEqualTo(
            "https://ep-"
            + liveEventName.substring(0, 23)
            + "-pre-mediakind-stg.uksouth.streaming.mediakind.com/"
            + liveEventName
            + "/index.qfm/manifest(format=m3u8-cmaf)");
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
        when(mockClient.getStreamingLocatorPaths(userId)).thenReturn(streamingPaths);

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).deleteStreamingLocator(userId);
        verify(mockClient, times(1)).createStreamingLocator(eq(userId), any(MkStreamingLocator.class));
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId);
    }

    @DisplayName("Should return the playback urls for the asset when content key policy didn't exist")
    @Test
    void playAssetContentKeyNotFoundSuccess() throws InterruptedException {
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
        when(mockClient.getStreamingLocatorPaths(userId)).thenReturn(streamingPaths);

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).putContentKeyPolicy(eq(userId), any(MkContentKeyPolicy.class));
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).deleteStreamingLocator(userId);
        verify(mockClient, times(1)).createStreamingLocator(eq(userId), any(MkStreamingLocator.class));
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId);
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
        when(mockClient.getStreamingLocatorPaths(userId)).thenReturn(streamingPaths);

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).putStreamingPolicy(eq("Predefined_ClearKey"), any(MkStreamingPolicy.class));
        verify(mockClient, times(1)).deleteStreamingLocator(userId);
        verify(mockClient, times(1)).createStreamingLocator(eq(userId), any(MkStreamingLocator.class));
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId);
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
        when(mockClient.getStreamingLocatorPaths(userId)).thenReturn(streamingPaths);

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).deleteStreamingLocator(userId);
        verify(mockClient, times(1)).createStreamingLocator(eq(userId), any(MkStreamingLocator.class));
        verify(mockClient, times(3)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId);
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
        when(mockClient.getStreamingLocatorPaths(userId)).thenReturn(streamingPaths);

        var playback = mediaKind.playAsset(assetName, userId);

        assertThat(playback.getDashUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getHlsUrl()).isEqualTo("https://example.com/playback/" + assetName);
        assertThat(playback.getToken()).isNotNull();
        assertThat(playback.getLicenseUrl()).isEqualTo("license url");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).deleteStreamingLocator(userId);
        verify(mockClient, times(1)).createStreamingLocator(eq(userId), any(MkStreamingLocator.class));
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).createStreamingEndpoint(eq("default"), any(MkStreamingEndpoint.class));
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId);
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
        when(mockClient.getStreamingLocatorPaths(userId)).thenReturn(streamingPaths);


        var message = assertThrows(
            NotFoundException.class,
            () -> mediaKind.playAsset(assetName, userId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Playback URL");

        verify(mockClient, times(1)).getAsset(assetName);
        verify(mockClient, times(1)).getContentKeyPolicy(userId);
        verify(mockClient, times(1)).getStreamingPolicy("Predefined_ClearKey");
        verify(mockClient, times(1)).deleteStreamingLocator(userId);
        verify(mockClient, times(1)).createStreamingLocator(eq(userId), any(MkStreamingLocator.class));
        verify(mockClient, times(1)).getStreamingEndpointByName("default");
        verify(mockClient, times(1)).getStreamingLocatorPaths(userId);
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
}
