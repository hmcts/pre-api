package uk.gov.hmcts.reform.preapi.tasks.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BatchImportMissingMkAssets.class)
@TestPropertySource(properties = {
    "tasks.batch-import-missing-mk-assets.batch-size=1",
    "tasks.batch-import-missing-mk-assets.poll-interval=10",
    "tasks.batch-import-missing-mk-assets.mp4-source-container=Video",
    "cron-user-email=test@test.com"
})
public class BatchImportMissingMkAssetsTest {
    @MockitoBean
    private IMediaService mediaService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    private static final String CRON_USER_EMAIL = "test@test.com";

    @Autowired
    private BatchImportMissingMkAssets batchImportMissingMkAssets;

    @BeforeEach
    void setUp() {
        var accessDto = mock(AccessDTO.class);
        var baseAppAccessDTO = mock(BaseAppAccessDTO.class);
        when(baseAppAccessDTO.getId()).thenReturn(UUID.randomUUID());
        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(accessDto);
        when(accessDto.getAppAccess()).thenReturn(Set.of(baseAppAccessDTO));
        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.ofNullable(userAuth));
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);

    }

    @Test
    void runVodafoneRecordingsEmpty() {
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of());
        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, never()).getAsset(any());
    }

    @Test
    void runNoRecordingsMissingAssets() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(new AssetDTO());
        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(1)).getAsset(any());
        verify(azureVodafoneStorageService, never()).getBlobUrlWithSasForCopy(any(), any());
    }

    @Test
    void runErrorCopying() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        doThrow(NotFoundException.class).when(azureVodafoneStorageService).getBlobUrlWithSasForCopy(any(), any());
        when(azureVodafoneStorageService.getStorageAccountName()).thenReturn("voda-sa");
        when(azureIngestStorageService.getStorageAccountName()).thenReturn("ingest-sa");

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(1)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlWithSasForCopy(any(), any());
        verify(azureVodafoneStorageService, never()).copyBlob(any(), any(), any());
        verify(azureVodafoneStorageService, times(1)).getStorageAccountName();
        verify(azureIngestStorageService, times(1)).getStorageAccountName();
    }

    @Test
    void runErrorCreatingIngest() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        when(azureVodafoneStorageService.getBlobUrlWithSasForCopy(any(), any())).thenReturn("example-url.com");
        when(mediaService.importAsset(recording, false)).thenReturn(false);

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(1)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlWithSasForCopy(any(), any());
        verify(azureIngestStorageService, times(1)).copyBlob(any(), any(), any());
        verify(azureVodafoneStorageService, never()).getStorageAccountName();
        verify(azureIngestStorageService, never()).getStorageAccountName();
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(false));
        verify(mediaService, never()).importAsset(any(RecordingDTO.class), eq(true));
    }

    @Test
    void runErrorCreatingFinal() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        when(azureVodafoneStorageService.getBlobUrlWithSasForCopy(any(), any())).thenReturn("example-url.com");
        when(mediaService.importAsset(recording, false)).thenReturn(true);
        when(mediaService.importAsset(recording, true)).thenReturn(false);

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(1)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlWithSasForCopy(any(), any());
        verify(azureIngestStorageService, times(1)).copyBlob(any(), any(), any());
        verify(azureVodafoneStorageService, never()).getStorageAccountName();
        verify(azureIngestStorageService, never()).getStorageAccountName();
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(false));
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(true));
        verify(mediaService, never()).triggerProcessingStep2(any(), anyBoolean());
    }

    @Test
    void runFinalMp4NotFound() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        when(azureVodafoneStorageService.getBlobUrlWithSasForCopy(any(), any())).thenReturn("example-url.com");
        when(mediaService.importAsset(recording, false)).thenReturn(true);
        when(mediaService.importAsset(recording, true)).thenReturn(true);
        String jobName = "job-name";
        when(mediaService.triggerProcessingStep2(recording.getId(), true)).thenReturn(jobName);
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName))
            .thenReturn(RecordingStatus.PROCESSING, RecordingStatus.PROCESSING, RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(recording.getId())).thenReturn(RecordingStatus.FAILURE);

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(1)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlWithSasForCopy(any(), any());
        verify(azureIngestStorageService, times(1)).copyBlob(any(), any(), any());
        verify(azureVodafoneStorageService, never()).getStorageAccountName();
        verify(azureIngestStorageService, never()).getStorageAccountName();
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(false));
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(true));
        verify(mediaService, times(1)).triggerProcessingStep2(recording.getId(), true);
        verify(mediaService, times(3)).hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName);
        verify(mediaService, times(1)).verifyFinalAssetExists(recording.getId());
        verify(azureFinalStorageService, never()).getMp4FileName(any());
        verify(azureFinalStorageService, never()).getRecordingDuration(any());
        verify(recordingService, never()).upsert(any(CreateRecordingDTO.class));
    }

    @Test
    void runSuccess() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        recording.setVersion(1);
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        when(azureVodafoneStorageService.getBlobUrlWithSasForCopy(any(), any())).thenReturn("example-url.com");
        when(mediaService.importAsset(recording, false)).thenReturn(true);
        when(mediaService.importAsset(recording, true)).thenReturn(true);
        String jobName = "job-name";
        when(mediaService.triggerProcessingStep2(recording.getId(), true)).thenReturn(jobName);
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName))
            .thenReturn(RecordingStatus.PROCESSING, RecordingStatus.PROCESSING, RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(recording.getId())).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(azureFinalStorageService.getMp4FileName(recording.getId().toString())).thenReturn("filename.mp4");
        when(azureFinalStorageService.getRecordingDuration(recording.getId())).thenReturn(Duration.ofMinutes(3));

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(1)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlWithSasForCopy(any(), any());
        verify(azureIngestStorageService, times(1)).copyBlob(any(), any(), any());
        verify(azureVodafoneStorageService, never()).getStorageAccountName();
        verify(azureIngestStorageService, never()).getStorageAccountName();
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(false));
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(true));
        verify(mediaService, times(1)).triggerProcessingStep2(recording.getId(), true);
        verify(mediaService, times(3)).hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName);
        verify(mediaService, times(1)).verifyFinalAssetExists(recording.getId());
        verify(azureFinalStorageService, times(1)).getMp4FileName(any());
        verify(azureFinalStorageService, times(1)).getRecordingDuration(any());
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
    }
}
