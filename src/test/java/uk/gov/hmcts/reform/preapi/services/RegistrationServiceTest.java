package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.dto.MkJob;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    private static final UUID CAPTURE_SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RECORDING_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final String liveEventId = getSanitisedLiveEventId(CAPTURE_SESSION_ID);
    private final String ingestJobName = format("%s-%d", getSanitisedLiveEventId(CAPTURE_SESSION_ID),
                                                Instant.now().getEpochSecond());
    private final MkJob ingestJob = mock(MkJob.class);

    private RegistrationService underTest;

    @BeforeEach
    void setUp() {
        mediaService = mock(IMediaService.class);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        underTest = new RegistrationService(captureSessionService, mediaServiceBroker, azureIngestStorageService);
    }

    @Test
    @DisplayName("Registering: Should throw exception when a running live event is found for capture session")
    public void testExceptionWhenActiveLiveEventFound() {
        LiveEventDTO liveEventDTO = mock(LiveEventDTO.class);
        when(mediaService.getLiveEvent(liveEventId)).thenReturn(liveEventDTO);
        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(CAPTURE_SESSION_ID)
        );
    }

    @Test
    @DisplayName("Registering: Should throw exception when registering capture session and ingest job is incomplete")
    public void testRegistrationExceptionWhenIngestJobIncomplete() {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, liveEventId))
            .thenReturn(ingestJob);
        MkJob.MkJobProperties properties = mock(MkJob.MkJobProperties.class);
        when(ingestJob.getProperties()).thenReturn(properties);
        when(properties.getState()).thenReturn(JobState.PROCESSING);

        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(CAPTURE_SESSION_ID)
        );
    }

    @Test
    @DisplayName("Registering: Should throw exception when no ingest job assets found")
    public void testExceptionWhenNoIngestJobAssetsFound() {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, liveEventId))
            .thenReturn(ingestJob);
        MkJob.MkJobProperties properties = mock(MkJob.MkJobProperties.class);
        when(ingestJob.getProperties()).thenReturn(properties);
        when(properties.getState()).thenReturn(JobState.FINISHED);
        when(properties.getOutputs()).thenReturn(null);
        assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.register(CAPTURE_SESSION_ID)
        );
    }

    @Test
    @DisplayName("Registering: should throw exception when registering capture session if recording not found")
    public void testRegistrationExceptionWhenNoRecordingFound() {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, liveEventId))
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
            () -> underTest.register(CAPTURE_SESSION_ID)
        );

        verify(mediaService, times(1)).verifyFinalAssetExists(any());
    }

    @Test
    @DisplayName("Registering: should successfully register capture session if jobs completed and recording found")
    public void testRegistrationWhenEncodingIsCompleted() {
        when(mediaService.getJobFromPartialName(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, liveEventId))
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

        UpsertResult register = underTest.register(CAPTURE_SESSION_ID);

        assertThat(register).isEqualTo(UpsertResult.UPDATED);

        verify(mediaService, times(1)).verifyFinalAssetExists(RECORDING_ID);
        verify(captureSessionService, times(1))
            .stopCaptureSession(CAPTURE_SESSION_ID, RecordingStatus.RECORDING_AVAILABLE, RECORDING_ID);

        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete("33333333-3333-3333-3333-333333333333");
        verify(azureIngestStorageService, times(1))
            .markContainerAsSafeToDelete(RECORDING_ID.toString());
    }
}
