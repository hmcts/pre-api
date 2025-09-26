package uk.gov.hmcts.reform.preapi.tasks.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private CaptureSessionService captureSessionService;

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

    private static final String CRON_USER_EMAIL = "vodafone@test.com";

    @Autowired
    private BatchImportMissingMkAssets batchImportMissingMkAssets;

    @BeforeEach
    void setUp() {
        var access = new AccessDTO();
        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));
        var userAuth = mock(UserAuthentication.class);
        when(userAuth.isAdmin()).thenReturn(true);
        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString())).thenReturn(Optional.of(userAuth));
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
    }

    @Test
    void runVodafoneRecordingsEmpty() throws IOException {
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of());
        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, never()).getAsset(any());
        ArgumentCaptor<String> filenameCaptor =  ArgumentCaptor.forClass(String.class);
        verify(azureFinalStorageService, never()).uploadBlob(filenameCaptor.capture(), any(), any());
    }

    @Test
    void runErrorCopying() throws IOException {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setCaseReference("REFERENCE");
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);

        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        doThrow(NotFoundException.class).when(azureVodafoneStorageService).getBlobUrlForCopy(any(), any());
        when(azureVodafoneStorageService.getStorageAccountName()).thenReturn("voda-sa");
        when(azureIngestStorageService.getStorageAccountName()).thenReturn("ingest-sa");

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(0)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlForCopy(any(), any());
        verify(azureVodafoneStorageService, never()).copyBlobOverwritable(any(), any(), any(), eq(false));
        verify(azureVodafoneStorageService, times(1)).getStorageAccountName();
        verify(azureIngestStorageService, times(1)).getStorageAccountName();
        verifyNoInteractions(captureSessionService);

        ArgumentCaptor<String> filenameCaptor =  ArgumentCaptor.forClass(String.class);
        verify(azureFinalStorageService, times(1)).uploadBlob(filenameCaptor.capture(), any(), any());

        String filename = filenameCaptor.getValue();
        assertThat(filename).contains("migration_report_");

        Path path = Paths.get(filename);
        List<String> lines = Files.readAllLines(path);
        assertThat(lines.size()).isEqualTo(2);
        assertReportHeaders(lines.getFirst());
        assertReportItem(lines.getLast(), recording, "0", RecordingStatus.FAILURE,
                         "Failed to copy blob 'null' between containers: voda-sa/Video -> ingest-sa/"
                             + recording.getId() + "-input");

        Files.deleteIfExists(path);
    }

    @Test
    void runErrorCreatingIngest() throws IOException {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        recording.setCaseReference("REFERENCE");
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);

        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        when(azureVodafoneStorageService.getBlobUrlForCopy(any(), any())).thenReturn("example-url.com");
        when(mediaService.importAsset(recording, false)).thenReturn(false);

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(1)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlForCopy(any(), any());
        verify(azureIngestStorageService, times(1)).copyBlobOverwritable(any(), any(), any(), eq(false));
        verify(azureVodafoneStorageService, never()).getStorageAccountName();
        verify(azureIngestStorageService, never()).getStorageAccountName();
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(false));
        verify(mediaService, never()).importAsset(any(RecordingDTO.class), eq(true));
        verifyNoInteractions(captureSessionService);

        ArgumentCaptor<String> filenameCaptor =  ArgumentCaptor.forClass(String.class);
        verify(azureFinalStorageService, times(1)).uploadBlob(filenameCaptor.capture(), any(), any());

        String filename = filenameCaptor.getValue();
        assertThat(filename).contains("migration_report_");

        Path path = Paths.get(filename);
        List<String> lines = Files.readAllLines(path);
        assertThat(lines.size()).isEqualTo(2);
        assertReportHeaders(lines.getFirst());
        assertReportItem(lines.getLast(), recording, "0", RecordingStatus.FAILURE, "Failed to create temporary asset");

        Files.deleteIfExists(path);
    }

    @Test
    void runErrorCreatingFinal() throws IOException {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        recording.setCaseReference("REFERENCE");
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);

        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        when(azureVodafoneStorageService.getBlobUrlForCopy(any(), any())).thenReturn("example-url.com");
        when(mediaService.importAsset(recording, false)).thenReturn(true);
        when(mediaService.importAsset(recording, true)).thenReturn(false);

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(2)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlForCopy(any(), any());
        verify(azureIngestStorageService, times(1)).copyBlobOverwritable(any(), any(), any(), eq(false));
        verify(azureVodafoneStorageService, never()).getStorageAccountName();
        verify(azureIngestStorageService, never()).getStorageAccountName();
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(false));
        verify(mediaService, times(1)).importAsset(any(RecordingDTO.class), eq(true));
        verify(mediaService, never()).triggerProcessingStep2(any(), anyBoolean());
        verifyNoInteractions(captureSessionService);

        ArgumentCaptor<String> filenameCaptor =  ArgumentCaptor.forClass(String.class);
        verify(azureFinalStorageService, times(1)).uploadBlob(filenameCaptor.capture(), any(), any());

        String filename = filenameCaptor.getValue();
        assertThat(filename).contains("migration_report_");

        Path path = Paths.get(filename);
        List<String> lines = Files.readAllLines(path);
        assertThat(lines.size()).isEqualTo(2);
        assertReportHeaders(lines.getFirst());
        assertReportItem(lines.getLast(), recording, "0", RecordingStatus.FAILURE,  "Failed to create final asset");

        Files.deleteIfExists(path);
    }

    @Test
    void runFinalMp4NotFound() throws IOException {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        recording.setCaseReference("REFERENCE");
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);

        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        when(azureVodafoneStorageService.getBlobUrlForCopy(any(), any())).thenReturn("example-url.com");
        when(mediaService.importAsset(recording, false)).thenReturn(true);
        when(mediaService.importAsset(recording, true)).thenReturn(true);
        String jobName = recording.getId().toString().replace("-", "") + "_output";
        when(mediaService.triggerProcessingStep2(recording.getId(), true)).thenReturn(jobName);
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName))
            .thenReturn(RecordingStatus.PROCESSING, RecordingStatus.PROCESSING, RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(recording.getId())).thenReturn(RecordingStatus.FAILURE);

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(2)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlForCopy(any(), any());
        verify(azureIngestStorageService, times(1)).copyBlobOverwritable(any(), any(), any(), eq(false));
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
        verifyNoInteractions(captureSessionService);

        ArgumentCaptor<String> filenameCaptor =  ArgumentCaptor.forClass(String.class);
        verify(azureFinalStorageService, times(1)).uploadBlob(filenameCaptor.capture(), any(), any());

        String filename = filenameCaptor.getValue();
        assertThat(filename).contains("migration_report_");

        Path path = Paths.get(filename);
        List<String> lines = Files.readAllLines(path);
        assertThat(lines.size()).isEqualTo(2);
        assertReportHeaders(lines.getFirst());
        assertReportItem(lines.getLast(), recording, "0", RecordingStatus.FAILURE,
                         "Final asset not found for recording after transform job");

        Files.deleteIfExists(path);
    }

    @Test
    void runSuccess() throws IOException {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        recording.setCaseReference("REFERENCE");
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        recording.setVersion(1);
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        when(azureVodafoneStorageService.getBlobUrlForCopy(any(), any())).thenReturn("example-url.com");
        when(mediaService.importAsset(recording, false)).thenReturn(true);
        when(mediaService.importAsset(recording, true)).thenReturn(true);
        String jobName = recording.getId().toString().replace("-", "") + "_output";
        when(mediaService.triggerProcessingStep2(recording.getId(), true)).thenReturn(jobName);
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName))
            .thenReturn(RecordingStatus.PROCESSING, RecordingStatus.PROCESSING, RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(recording.getId())).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(azureFinalStorageService.getMp4FileName(recording.getId().toString())).thenReturn("filename.mp4");
        when(azureFinalStorageService.getRecordingDuration(recording.getId())).thenReturn(Duration.ofMinutes(3));

        batchImportMissingMkAssets.run();

        verify(recordingService, times(1)).findAllVodafoneRecordings();
        verify(mediaService, times(2)).getAsset(any());
        verify(azureVodafoneStorageService, times(1)).getBlobUrlForCopy(any(), any());
        verify(azureIngestStorageService, times(1)).copyBlobOverwritable(any(), any(), any(), eq(false));
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

        ArgumentCaptor<CreateCaptureSessionDTO> createCaptureSession =
            ArgumentCaptor.forClass(CreateCaptureSessionDTO.class);
        verify(captureSessionService, times(1)).upsert(createCaptureSession.capture());
        assertThat(createCaptureSession.getValue().getStatus()).isEqualTo(RecordingStatus.RECORDING_AVAILABLE);


        ArgumentCaptor<String> filenameCaptor =  ArgumentCaptor.forClass(String.class);
        verify(azureFinalStorageService, times(1)).uploadBlob(filenameCaptor.capture(), any(), any());

        String filename = filenameCaptor.getValue();
        assertThat(filename).contains("migration_report_");

        Path path = Paths.get(filename);
        List<String> lines = Files.readAllLines(path);
        assertThat(lines.size()).isEqualTo(2);
        assertReportHeaders(lines.getFirst());
        assertReportItem(lines.getLast(), recording, "180", RecordingStatus.RECORDING_AVAILABLE, null);

        Files.deleteIfExists(path);
    }

    private void assertReportHeaders(String headerLine) {
        assertThat(headerLine).isEqualTo("RecordingId,CaseReference,Filename,Duration,MigrationStatus,ErrorMessage");
    }

    private void assertReportItem(String reportLine,
                                  RecordingDTO recording,
                                  String duration,
                                  RecordingStatus status,
                                  String errorMessage) {
        String[] reportItem =  reportLine.split(",");

        assertThat(reportItem).hasSize(errorMessage != null ? 6 : 5);
        assertThat(reportItem[0]).isEqualTo(recording.getId().toString());
        assertThat(reportItem[1]).isEqualTo(recording.getCaseReference());
        assertThat(reportItem[2]).isEqualTo(recording.getFilename() != null ? recording.getFilename() : "");
        assertThat(reportItem[3]).isEqualTo(duration);
        assertThat(reportItem[4]).isEqualTo(status.toString());
        if (errorMessage != null) {
            assertThat(reportItem[5]).isEqualTo(errorMessage);
        }
    }

    @Test
    @DisplayName("Should run async successfully")
    void asyncRunExecutesSuccessfully() {
        BatchImportMissingMkAssets spyBatchImport = spy(batchImportMissingMkAssets);
        doNothing().when(spyBatchImport).run();

        spyBatchImport.asyncRun();

        verify(spyBatchImport, times(1)).run();
    }

    @Test
    @DisplayName("Should catch any error from job running async")
    void asyncRunCatchesRuntimeException() {
        BatchImportMissingMkAssets spyBatchImport = spy(batchImportMissingMkAssets);
        doThrow(new RuntimeException("Test exception")).when(spyBatchImport).run();

        spyBatchImport.asyncRun();

        verify(spyBatchImport, times(1)).run();
    }

    @Test
    void runFailureCantFindRecordingByJobId() {
        RecordingDTO recording = new RecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        recording.setCaseReference("REFERENCE");
        CaptureSessionDTO captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        recording.setVersion(1);
        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(recording));
        when(mediaService.getAsset(any())).thenReturn(null);
        when(azureVodafoneStorageService.getBlobUrlForCopy(any(), any())).thenReturn("example-url.com");
        when(mediaService.importAsset(recording, false)).thenReturn(true);
        when(mediaService.importAsset(recording, true)).thenReturn(true);
        String jobName = recording.getId().toString() + "_output";
        when(mediaService.triggerProcessingStep2(recording.getId(), true)).thenReturn(jobName);
        when(mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName))
            .thenReturn(RecordingStatus.PROCESSING, RecordingStatus.PROCESSING, RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(recording.getId())).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(azureFinalStorageService.getMp4FileName(recording.getId().toString())).thenReturn("filename.mp4");
        when(azureFinalStorageService.getRecordingDuration(recording.getId())).thenReturn(Duration.ofMinutes(3));

        batchImportMissingMkAssets.run();

        verify(mediaService, times(0)).verifyFinalAssetExists(recording.getId());
    }
}
