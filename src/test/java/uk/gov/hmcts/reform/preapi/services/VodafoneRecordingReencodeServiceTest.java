package uk.gov.hmcts.reform.preapi.services;

import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.RecordingReencodeJob;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VodafoneRecordingReencodeServiceTest {

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private CaptureSessionRepository captureSessionRepository;

    @Mock
    private MigrationRecordRepository migrationRecordRepository;

    @Mock
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @Mock
    private AzureIngestStorageService azureIngestStorageService;

    @Mock
    private AzureFinalStorageService azureFinalStorageService;

    @Mock
    private MediaServiceBroker mediaServiceBroker;

    @Mock
    private IMediaService mediaService;

    @Mock
    private CommandExecutor commandExecutor;

    @Mock
    private RecordingService recordingService;

    @Mock
    private CaptureSessionService captureSessionService;

    private VodafoneRecordingReencodeService underTest;

    @BeforeEach
    void setUp() {
        underTest = new VodafoneRecordingReencodeService(
            recordingRepository,
            captureSessionRepository,
            migrationRecordRepository,
            azureVodafoneStorageService,
            azureIngestStorageService,
            azureFinalStorageService,
            mediaServiceBroker,
            commandExecutor,
            recordingService,
            captureSessionService,
            1L,
            10L
        );
    }

    @Test
    @DisplayName("generateReencodeCommand should include the expected ffmpeg arguments")
    void generateReencodeCommandContainsExpectedArguments() {
        CommandLine commandLine = underTest.generateReencodeCommand(
            Path.of("/tmp/input.mp4"),
            Path.of("/tmp/output.mp4")
        );

        String command = commandLine.toString();
        assertThat(command).contains("ffmpeg");
        assertThat(command).contains("-fflags");
        assertThat(command).contains("+genpts");
        assertThat(command).contains("-err_detect");
        assertThat(command).contains("ignore_err");
        assertThat(command).contains("libx264");
        assertThat(command).contains("aresample=async=1:first_pts=0");
        assertThat(command).contains("aac");
        assertThat(command).contains("128k");
        assertThat(command).contains("+faststart");
    }

    @Test
    @DisplayName("processJob should re-encode, overwrite ingest input and sync recording metadata")
    void processJobSuccess() {
        UUID recordingId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        UUID migrationRecordId = UUID.randomUUID();

        Recording recording = createRecording(recordingId, captureSessionId);
        CaptureSession captureSession = recording.getCaptureSession();

        RecordingReencodeJob job = new RecordingReencodeJob();
        job.setId(UUID.randomUUID());
        job.setRecordingId(recordingId);
        job.setCaptureSessionId(captureSessionId);
        job.setMigrationRecordId(migrationRecordId);
        job.setSourceContainer("prod-migration-2");
        job.setSourceBlobName("source.mp4");
        job.setReencodedBlobName("source-pre-reencoded.mp4");

        when(recordingRepository.findByIdAndDeletedAtIsNull(recordingId)).thenReturn(Optional.of(recording));
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSessionId)).thenReturn(Optional.of(captureSession));
        when(migrationRecordRepository.findById(migrationRecordId)).thenReturn(Optional.of(new MigrationRecord()));
        when(azureVodafoneStorageService.downloadBlob(eq("prod-migration-2"), eq("source.mp4"), any())).thenReturn(true);
        when(commandExecutor.execute(any())).thenReturn(true);
        when(azureVodafoneStorageService.uploadBlob(any(), eq("prod-migration-2"), eq("source-pre-reencoded.mp4")))
            .thenReturn(true);
        when(azureVodafoneStorageService.getBlobUrlForCopy("prod-migration-2", "source-pre-reencoded.mp4"))
            .thenReturn("https://blob");
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getAsset(recordingId.toString().replace("-", "") + "_temp")).thenReturn(new AssetDTO());
        when(mediaService.getAsset(recordingId.toString().replace("-", "") + "_output")).thenReturn(new AssetDTO());
        when(mediaService.triggerProcessingStep2(recordingId, true)).thenReturn("mk-job");
        when(mediaService.hasJobCompleted(any(), eq("mk-job"))).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(mediaService.verifyFinalAssetExists(recordingId)).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(azureFinalStorageService.getMp4FileName(recordingId.toString())).thenReturn("final.mp4");
        when(azureFinalStorageService.getRecordingDuration(recordingId)).thenReturn(Duration.ofSeconds(12));

        underTest.processJob(job);

        verify(azureIngestStorageService).copyBlobOverwritable(
            recordingId + "-input",
            "original.mp4",
            "https://blob",
            true
        );

        ArgumentCaptor<CreateRecordingDTO> recordingCaptor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
        verify(recordingService).forceUpsert(recordingCaptor.capture());
        assertThat(recordingCaptor.getValue().getId()).isEqualTo(recordingId);
        assertThat(recordingCaptor.getValue().getFilename()).isEqualTo("final.mp4");
        assertThat(recordingCaptor.getValue().getDuration()).isEqualTo(Duration.ofSeconds(12));

        ArgumentCaptor<CreateCaptureSessionDTO> captureCaptor = ArgumentCaptor.forClass(CreateCaptureSessionDTO.class);
        verify(captureSessionService).upsert(captureCaptor.capture());
        assertThat(captureCaptor.getValue().getId()).isEqualTo(captureSessionId);
        assertThat(captureCaptor.getValue().getStatus()).isEqualTo(RecordingStatus.RECORDING_AVAILABLE);
    }

    private Recording createRecording(UUID recordingId, UUID captureSessionId) {
        Court court = new Court();
        court.setId(UUID.randomUUID());
        court.setCourtType(CourtType.CROWN);
        court.setName("Court");

        Case aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);
        aCase.setReference("CASE-1");
        aCase.setState(CaseState.OPEN);

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);

        CaptureSession captureSession = new CaptureSession();
        captureSession.setId(captureSessionId);
        captureSession.setBooking(booking);
        captureSession.setOrigin(RecordingOrigin.VODAFONE);
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        Recording recording = new Recording();
        recording.setId(recordingId);
        recording.setCaptureSession(captureSession);
        recording.setFilename("original.mp4");
        recording.setVersion(1);
        return recording;
    }
}
