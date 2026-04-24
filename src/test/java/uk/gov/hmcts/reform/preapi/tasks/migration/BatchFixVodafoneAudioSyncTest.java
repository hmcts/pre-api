package uk.gov.hmcts.reform.preapi.tasks.migration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.edit.FfmpegService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BatchFixVodafoneAudioSync.class)
@TestPropertySource(properties = {
    "tasks.batch-fix-vodafone-audio-sync.poll-interval=10",
    "tasks.batch-fix-vodafone-audio-sync.max-sleep-time=25",
    "tasks.batch-fix-vodafone-audio-sync.mp4-source-container=Video",
    "vodafone-user-email=vodafone@test.com"
})
class BatchFixVodafoneAudioSyncTest {
    @MockitoBean
    private IMediaService mediaService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private CaptureSessionService captureSessionService;

    @MockitoBean
    private MigrationRecordRepository migrationRecordRepository;

    @MockitoBean
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private FfmpegService ffmpegService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @Autowired
    private BatchFixVodafoneAudioSync batchFixVodafoneAudioSync;

    private Path createdReport;

    @BeforeEach
    void setUp() {
        var access = new AccessDTO();
        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));
        var userAuth = mock(UserAuthentication.class);
        when(userAuth.isAdmin()).thenReturn(true);
        when(userService.findByEmail("vodafone@test.com")).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString())).thenReturn(Optional.of(userAuth));
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (createdReport != null) {
            Files.deleteIfExists(createdReport);
        }
    }

    @Test
    @DisplayName("Should do nothing when there are no Vodafone root recordings to repair")
    void runWithNoVodafoneRecordings() {
        when(recordingService.findAllVodafoneRootRecordings()).thenReturn(List.of());

        batchFixVodafoneAudioSync.run();

        verify(recordingService, times(1)).findAllVodafoneRootRecordings();
        verify(azureFinalStorageService, never()).uploadBlob(any(), any(), any());
    }

    @Test
    @DisplayName("Should restore missing ingest blob, repair it, and rerun EncodeFromMp4")
    void runRepairsRecordingAndUpdatesMetadata() throws IOException {
        RecordingDTO recording = createRecording();
        MigrationRecord migrationRecord = createMigrationRecord(recording, "archive/A1/mp4/input.mp4");
        String repairedBlob = "archive/A1/mp4/input-syncfix.mp4";
        String jobName = recording.getId().toString().replace("-", "") + "_temp-123";

        when(recordingService.findAllVodafoneRootRecordings()).thenReturn(List.of(recording));
        when(migrationRecordRepository.findTopByRecordingIdOrderByCreatedAtDesc(recording.getId()))
            .thenReturn(Optional.of(migrationRecord));
        when(azureIngestStorageService.doesBlobExist(recording.getId() + "-input", repairedBlob)).thenReturn(false);
        when(azureIngestStorageService.doesBlobExist(recording.getId() + "-input", migrationRecord.getFileName()))
            .thenReturn(false);
        when(azureVodafoneStorageService.getBlobUrlForCopy("Video", migrationRecord.getFileName())).thenReturn("blob");
        when(mediaService.getAsset(any())).thenReturn(mock(uk.gov.hmcts.reform.preapi.dto.media.AssetDTO.class));
        when(mediaService.triggerProcessingStep2(recording.getId(), true, repairedBlob)).thenReturn(jobName);
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(recording.getId())).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(azureFinalStorageService.getMp4FileName(recording.getId().toString())).thenReturn(repairedBlob);
        when(azureFinalStorageService.getRecordingDuration(recording.getId())).thenReturn(Duration.ofMinutes(3));

        captureUploadedReport();
        batchFixVodafoneAudioSync.run();

        verify(azureIngestStorageService, times(1)).copyBlobOverwritable(
            eq(recording.getId() + "-input"),
            eq(migrationRecord.getFileName()),
            eq("blob"),
            eq(false)
        );
        verify(ffmpegService, times(1)).trimToSecondKeyframeInIngest(
            recording.getId(),
            migrationRecord.getFileName(),
            repairedBlob
        );
        verify(mediaService, times(1)).triggerProcessingStep2(recording.getId(), true, repairedBlob);
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
        verify(captureSessionService, times(1)).upsert(any(CreateCaptureSessionDTO.class));
        verify(azureFinalStorageService, times(1)).uploadBlob(any(), eq("mk-import-reports"), any());
    }

    @Test
    @DisplayName("Should reuse an existing repaired ingest blob without copying or trimming again")
    void runSkipsRestoreAndTrimWhenRepairedBlobAlreadyExists() throws IOException {
        RecordingDTO recording = createRecording();
        MigrationRecord migrationRecord = createMigrationRecord(recording, "archive/A1/mp4/input.mp4");
        String repairedBlob = "archive/A1/mp4/input-syncfix.mp4";
        String jobName = recording.getId().toString().replace("-", "") + "_temp-123";

        when(recordingService.findAllVodafoneRootRecordings()).thenReturn(List.of(recording));
        when(migrationRecordRepository.findTopByRecordingIdOrderByCreatedAtDesc(recording.getId()))
            .thenReturn(Optional.of(migrationRecord));
        when(azureIngestStorageService.doesBlobExist(recording.getId() + "-input", repairedBlob)).thenReturn(true);
        when(mediaService.getAsset(any())).thenReturn(mock(uk.gov.hmcts.reform.preapi.dto.media.AssetDTO.class));
        when(mediaService.triggerProcessingStep2(recording.getId(), true, repairedBlob)).thenReturn(jobName);
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName))
            .thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(recording.getId())).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(azureFinalStorageService.getMp4FileName(recording.getId().toString())).thenReturn(repairedBlob);
        when(azureFinalStorageService.getRecordingDuration(recording.getId())).thenReturn(Duration.ofMinutes(3));

        captureUploadedReport();
        batchFixVodafoneAudioSync.run();

        verify(azureIngestStorageService, never()).copyBlobOverwritable(any(), any(), any(), anyBoolean());
        verify(ffmpegService, never()).trimToSecondKeyframeInIngest(any(), any(), any());
        verify(mediaService, times(1)).triggerProcessingStep2(recording.getId(), true, repairedBlob);
    }

    @Test
    @DisplayName("Should report failure when the migration record cannot be found")
    void runAddsFailureWhenMigrationRecordMissing() throws IOException {
        RecordingDTO recording = createRecording();

        when(recordingService.findAllVodafoneRootRecordings()).thenReturn(List.of(recording));
        when(migrationRecordRepository.findTopByRecordingIdOrderByCreatedAtDesc(recording.getId()))
            .thenReturn(Optional.empty());
        when(migrationRecordRepository.findTopByCaptureSessionIdOrderByCreatedAtDesc(recording.getCaptureSession().getId()))
            .thenReturn(Optional.empty());

        captureUploadedReport();
        batchFixVodafoneAudioSync.run();

        verify(ffmpegService, never()).trimToSecondKeyframeInIngest(any(), any(), any());
        verify(mediaService, never()).triggerProcessingStep2(any(), anyBoolean(), any());
        verify(azureFinalStorageService, times(1)).uploadBlob(any(), eq("mk-import-reports"), any());

        List<String> lines = Files.readAllLines(createdReport);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains("FAILURE");
        assertThat(lines.get(1)).contains("Could not locate vf_migration_records row");
    }

    private RecordingDTO createRecording() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("archive/A1/mp4/input.mp4");
        recording.setCaseReference("CASE-123");

        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        return recording;
    }

    private MigrationRecord createMigrationRecord(RecordingDTO recording, String fileName) {
        MigrationRecord migrationRecord = new MigrationRecord();
        migrationRecord.setRecordingId(recording.getId());
        migrationRecord.setCaptureSessionId(recording.getCaptureSession().getId());
        migrationRecord.setFileName(fileName);
        return migrationRecord;
    }

    private void captureUploadedReport() {
        when(azureFinalStorageService.uploadBlob(any(), eq("mk-import-reports"), any()))
            .thenAnswer(invocation -> {
                createdReport = Paths.get(invocation.getArgument(0, String.class));
                return true;
            });
    }
}
