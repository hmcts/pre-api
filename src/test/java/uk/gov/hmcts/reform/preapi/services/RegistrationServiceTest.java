package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.dto.MkJob;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getSanitisedLiveEventId;

@SpringBootTest(classes = RegistrationService.class)
public class RegistrationServiceTest {

    @MockitoBean
    private CaptureSessionService captureSessionService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private IMediaService mediaService;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private CaptureSessionDTO captureSessionDTO;

    private static final UUID CAPTURE_SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RECORDING_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String LIVE_EVENT_ID = getSanitisedLiveEventId(CAPTURE_SESSION_ID);
    private final MkJob ingestJob = mock(MkJob.class);

    private RegistrationService underTest;

    @BeforeEach
    void setUp() throws InterruptedException {
        mediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);

        when(captureSessionService.findById(CAPTURE_SESSION_ID)).thenReturn(captureSessionDTO);
        when(captureSessionDTO.getId()).thenReturn(CAPTURE_SESSION_ID);
        when(captureSessionDTO.getLiveOutputUrl()).thenReturn(LIVE_EVENT_ID);
        when(captureSessionDTO.getStatus()).thenReturn(RecordingStatus.PROCESSING);
        when(captureSessionDTO.getFinishedAt())
            .thenReturn(Timestamp.from(Instant.now().minus(30, ChronoUnit.MINUTES)));

        SearchRecordings params = new SearchRecordings();
        params.setCaptureSessionId(CAPTURE_SESSION_ID);
        when(recordingService.findAll(params, false, Pageable.unpaged())).thenReturn(Page.empty());

        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID))
            .thenReturn(ingestJob);

        when(mediaService.processCaptureSession(eq(captureSessionDTO), any(), eq(LIVE_EVENT_ID)))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);

        underTest = new RegistrationService(
            captureSessionService, mediaServiceBroker,
            azureIngestStorageService, 30, recordingService
        );
    }

    @Test
    @DisplayName("Registering: should successfully register capture session with already-processed recording if"
        + " jobs completed and recording found")
    public void testRegistrationWhenEncodingIsCompleted() throws InterruptedException {
        when(mediaService.getLiveEvent(LIVE_EVENT_ID)).thenThrow(NotFoundException.class);
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID))
            .thenReturn(ingestJob);
        MkJob.MkJobProperties properties = mock(MkJob.MkJobProperties.class);
        when(ingestJob.getProperties()).thenReturn(properties);
        when(properties.getState()).thenReturn(JobState.FINISHED);
        List<JobOutputAsset> jobOutputAssets = new ArrayList<>();
        JobOutputAsset jobOutputAsset = mock(JobOutputAsset.class);
        jobOutputAssets.add(jobOutputAsset);
        when(properties.getOutputs()).thenReturn(jobOutputAssets);
        AssetDTO assetDTO = mock(AssetDTO.class);
        when(jobOutputAsset.assetName()).thenReturn("2222222222222222222222222222222_temp");
        when(mediaService.getAsset("2222222222222222222222222222222_temp")).thenReturn(assetDTO);
        when(assetDTO.getContainer()).thenReturn(RECORDING_ID.toString());
        when(mediaService.verifyFinalAssetExists(any())).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        CaptureSessionDTO captureSessionDTO = new CaptureSessionDTO();
        captureSessionDTO.setId(CAPTURE_SESSION_ID);
        captureSessionDTO.setBookingId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        when(captureSessionService
                 .stopCaptureSession(CAPTURE_SESSION_ID, RecordingStatus.RECORDING_AVAILABLE, RECORDING_ID))
            .thenReturn(captureSessionDTO);

        UpsertResult register = underTest.register(CAPTURE_SESSION_ID, false);

        assertThat(register).isEqualTo(UpsertResult.UPDATED);

        // Should skip the re-encoding jobs
        verifyNoInteractions(recordingService);
        verify(mediaService, times(0)).processCaptureSession(any(), any(), any());

        verify(mediaService, times(1)).verifyFinalAssetExists(RECORDING_ID);
        verify(captureSessionService, times(1))
            .stopCaptureSession(CAPTURE_SESSION_ID, RecordingStatus.RECORDING_AVAILABLE, RECORDING_ID);

        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete("33333333-3333-3333-3333-333333333333");
        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete(RECORDING_ID.toString());
    }

    @Test
    @DisplayName("Registering: Should throw exception when a running live event is found for capture session")
    public void testExceptionWhenActiveLiveEventFound() {
        when(mediaService.validateLiveEventIsFinished(LIVE_EVENT_ID, CAPTURE_SESSION_ID))
            .thenThrow(ResourceInWrongStateException.class);
        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(CAPTURE_SESSION_ID, false)
        );
    }

    @Test
    @DisplayName("Registering: Should reprocess when ingest job is incomplete")
    public void testRegistrationExceptionWhenIngestJobIncomplete() throws InterruptedException {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID))
            .thenReturn(ingestJob);
        MkJob.MkJobProperties properties = mock(MkJob.MkJobProperties.class);
        when(ingestJob.getProperties()).thenReturn(properties);
        when(properties.getState()).thenReturn(JobState.PROCESSING);

        UpsertResult result = underTest.register(CAPTURE_SESSION_ID, false);
        assertThat(result).isEqualTo(UpsertResult.UPDATED);

        verify(mediaService, times(1))
            .getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID);
        verify(mediaService, times(1))
            .validateLiveEventIsFinished(LIVE_EVENT_ID, CAPTURE_SESSION_ID);
        verify(mediaService, times(1)).processCaptureSession(any(), any(), any());

        verify(captureSessionService, times(1)).stopCaptureSession(
            eq(CAPTURE_SESSION_ID), eq(RecordingStatus.RECORDING_AVAILABLE), any(UUID.class));
    }

    @Test
    @DisplayName("Registering: Should reprocess when ingest job is null")
    public void testRegistrationExceptionWhenIngestJobNull() throws InterruptedException {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID))
            .thenReturn(null);

        UpsertResult result = underTest.register(CAPTURE_SESSION_ID, false);

        assertThat(result).isEqualTo(UpsertResult.UPDATED);

        verify(mediaService, times(1))
            .getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID);
        verify(mediaService, times(1))
            .validateLiveEventIsFinished(LIVE_EVENT_ID, CAPTURE_SESSION_ID);
        verify(mediaService, times(1)).processCaptureSession(any(), any(), any());

        verify(captureSessionService, times(1)).stopCaptureSession(
            eq(CAPTURE_SESSION_ID), eq(RecordingStatus.RECORDING_AVAILABLE), any(UUID.class));
    }

    @Test
    @DisplayName("Registering: Should reprocess when no ingest job assets found")
    public void testExceptionWhenNoIngestJobAssetsFound() throws InterruptedException {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID))
            .thenReturn(ingestJob);
        MkJob.MkJobProperties properties = mock(MkJob.MkJobProperties.class);
        when(ingestJob.getProperties()).thenReturn(properties);
        when(properties.getState()).thenReturn(JobState.FINISHED);
        when(properties.getOutputs()).thenReturn(null);
        underTest.register(CAPTURE_SESSION_ID, false);

        verify(mediaService, times(1))
            .getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID);
        verify(mediaService, times(1))
            .validateLiveEventIsFinished(LIVE_EVENT_ID, CAPTURE_SESSION_ID);
        verify(mediaService, times(1)).processCaptureSession(any(), any(), any());
    }

    @Test
    @DisplayName("Registering: Should force reprocessing if flag is set")
    public void testForceReprocessing() throws InterruptedException {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID))
            .thenReturn(ingestJob);
        MkJob.MkJobProperties properties = mock(MkJob.MkJobProperties.class);
        when(ingestJob.getProperties()).thenReturn(properties);
        when(properties.getState()).thenReturn(JobState.FINISHED);
        List<JobOutputAsset> jobOutputAssets = new ArrayList<>();
        JobOutputAsset jobOutputAsset = mock(JobOutputAsset.class);
        jobOutputAssets.add(jobOutputAsset);
        when(properties.getOutputs()).thenReturn(jobOutputAssets);
        underTest.register(CAPTURE_SESSION_ID, true);

        verify(mediaService, times(1))
            .getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID);
        verify(mediaService, times(1))
            .validateLiveEventIsFinished(LIVE_EVENT_ID, CAPTURE_SESSION_ID);
        verify(mediaService, times(1)).processCaptureSession(any(), any(), any());

    }

    @Test
    @DisplayName("Registering: Should delete any existing recordings when reprocessing a capture session")
    public void testExistingRecordingsAreDeletedWhenReprocessing() throws InterruptedException {
        RecordingDTO recording1 = mock(RecordingDTO.class);
        RecordingDTO recording2 = mock(RecordingDTO.class);
        when(recording1.getId()).thenReturn(UUID.randomUUID());
        when(recording2.getId()).thenReturn(UUID.randomUUID());

        when(recordingService.findAll(any(), eq(false), eq(Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(recording1, recording2)));
        underTest.register(CAPTURE_SESSION_ID, true);

        verify(mediaService, times(1))
            .getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID);
        verify(mediaService, times(1))
            .validateLiveEventIsFinished(LIVE_EVENT_ID, CAPTURE_SESSION_ID);
        verify(mediaService, times(1)).processCaptureSession(any(), any(), any());

        ArgumentCaptor<SearchRecordings> searchRecordingsCaptor = ArgumentCaptor.forClass(SearchRecordings.class);
        verify(recordingService, times(1)).findAll(searchRecordingsCaptor.capture(),
                                                   eq(false),
                                                   eq(Pageable.unpaged()));
        assertThat(searchRecordingsCaptor.getValue().getCaptureSessionId()).isEqualTo(CAPTURE_SESSION_ID);
        verify(recordingService, times(1)).deleteById(recording1.getId());
        verify(recordingService, times(1)).deleteById(recording2.getId());

    }

    @Test
    @DisplayName("Registering: should throw exception when registering capture session if recording not found")
    public void testRegistrationExceptionWhenNoRecordingFound() {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID))
            .thenReturn(ingestJob);
        MkJob.MkJobProperties properties = mock(MkJob.MkJobProperties.class);
        when(ingestJob.getProperties()).thenReturn(properties);
        when(properties.getState()).thenReturn(JobState.FINISHED);
        List<JobOutputAsset> jobOutputAssets = new ArrayList<>();
        JobOutputAsset jobOutputAsset = mock(JobOutputAsset.class);
        jobOutputAssets.add(jobOutputAsset);
        when(properties.getOutputs()).thenReturn(jobOutputAssets);
        AssetDTO assetDTO = mock(AssetDTO.class);
        when(jobOutputAsset.assetName()).thenReturn("2222222222222222222222222222222_temp");
        when(mediaService.getAsset("2222222222222222222222222222222_temp")).thenReturn(assetDTO);
        when(assetDTO.getContainer()).thenReturn(RECORDING_ID.toString());

        when(mediaService.verifyFinalAssetExists(any())).thenReturn(RecordingStatus.FAILURE);
        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(CAPTURE_SESSION_ID, false)
        );

        verify(mediaService, times(1)).verifyFinalAssetExists(any());
    }

    @Test
    @DisplayName("Registering: should throw exception when registering capture session if recording fails")
    public void testRegistrationExceptionWhenRecordingFails() throws InterruptedException {
        when(mediaService.processCaptureSession(eq(captureSessionDTO), any(UUID.class), eq(LIVE_EVENT_ID)))
            .thenReturn(RecordingStatus.FAILURE);

        assertThrows(
            UnknownServerException.class,
            () -> underTest.register(CAPTURE_SESSION_ID, true)
        );

        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(CAPTURE_SESSION_ID), eq(RecordingStatus.FAILURE), eq(null));
    }


    @ParameterizedTest
    @NullSource
    @EmptySource
    @DisplayName("Should reprocess if job found but it has no output assets")
    public void testRegistrationExceptionWhenIngestJobHasNoOutputAssets(List<JobOutputAsset> jobOutputAssets)
        throws InterruptedException {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID))
            .thenReturn(ingestJob);
        MkJob.MkJobProperties properties = mock(MkJob.MkJobProperties.class);
        when(ingestJob.getProperties()).thenReturn(properties);
        when(properties.getState()).thenReturn(JobState.FINISHED);
        when(properties.getOutputs()).thenReturn(jobOutputAssets);

        underTest.register(CAPTURE_SESSION_ID, false);

        verify(mediaService, times(1))
            .getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, LIVE_EVENT_ID);
        verify(mediaService, times(1))
            .validateLiveEventIsFinished(LIVE_EVENT_ID, CAPTURE_SESSION_ID);

        verify(mediaService, times(1))
            .processCaptureSession(eq(captureSessionDTO), any(UUID.class), eq(LIVE_EVENT_ID));

        verify(captureSessionService, times(1)).stopCaptureSession(
            eq(CAPTURE_SESSION_ID), eq(RecordingStatus.RECORDING_AVAILABLE), any(UUID.class));
    }

    @Test
    @DisplayName("Verification: should throw exception when capture session is not found in database")
    public void testVerificationExceptionWhenCaptureSessionNotFound() {
        when(captureSessionService.findById(CAPTURE_SESSION_ID)).thenThrow(NotFoundException.class);
        assertThrows(
            BadRequestException.class,
            () -> underTest.register(CAPTURE_SESSION_ID, false)
        );
    }

    @Test
    @DisplayName("Verification: should throw exception if capture session was finished within timeout window")
    public void testVerificationExceptionWhenCaptureSessionFinishedRecently() {
        when(captureSessionDTO.getFinishedAt())
            .thenReturn(Timestamp.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(CAPTURE_SESSION_ID, true)
        );
    }

    @ParameterizedTest
    @EnumSource(value = RecordingStatus.class, names = {"INITIALISING", "STANDBY", "RECORDING", "RECORDING_AVAILABLE"})
    @DisplayName("Verification: should throw exception when capture session is ready for processing")
    public void testVerificationExceptionWhenCaptureSessionNotReady(RecordingStatus status) {
        when(captureSessionDTO.getStatus()).thenReturn(status);
        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(CAPTURE_SESSION_ID, true)
        );
    }

    @ParameterizedTest
    @EnumSource(value = RecordingStatus.class, names = {"PROCESSING", "NO_RECORDING", "FAILURE"})
    @DisplayName("Verification: should register when capture session is ready for processing")
    public void testShouldRegisterWhenCaptureSessionIsReady(RecordingStatus status)
        throws InterruptedException {
        when(captureSessionDTO.getStatus()).thenReturn(status);
        UpsertResult register = underTest.register(CAPTURE_SESSION_ID, true);
        assertThat(register).isEqualTo(UpsertResult.UPDATED);
    }

}
