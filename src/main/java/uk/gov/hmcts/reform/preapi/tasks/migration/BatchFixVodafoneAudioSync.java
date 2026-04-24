package uk.gov.hmcts.reform.preapi.tasks.migration;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.edit.FfmpegService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.tasks.RobotUserTask;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class BatchFixVodafoneAudioSync extends RobotUserTask {
    private static final String REPORT_CONTAINER = "mk-import-reports";
    private static final String REPAIRED_SUFFIX = "-syncfix";

    private final MediaServiceBroker mediaServiceBroker;
    private final RecordingService recordingService;
    private final CaptureSessionService captureSessionService;
    private final MigrationRecordRepository migrationRecordRepository;
    private final AzureVodafoneStorageService azureVodafoneStorageService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final FfmpegService ffmpegService;

    @Value("${tasks.batch-fix-vodafone-audio-sync.poll-interval:"
        + "${tasks.batch-import-missing-mk-assets.poll-interval:10000}}")
    private long pollInterval;

    @Value("${tasks.batch-fix-vodafone-audio-sync.max-sleep-time:"
        + "${tasks.batch-import-missing-mk-assets.max-sleep-time:3600000}}")
    private long maxSleepTime;

    @Value("${tasks.batch-fix-vodafone-audio-sync.mp4-source-container:"
        + "${tasks.batch-import-missing-mk-assets.mp4-source-container:Video}}")
    private String vfSourceContainer;

    private List<ReportItem> reportItems;

    @Autowired
    public BatchFixVodafoneAudioSync(UserService userService,
                                     UserAuthenticationService userAuthenticationService,
                                     @Value("${vodafone-user-email}") String cronUserEmail,
                                     MediaServiceBroker mediaServiceBroker,
                                     RecordingService recordingService,
                                     CaptureSessionService captureSessionService,
                                     MigrationRecordRepository migrationRecordRepository,
                                     AzureVodafoneStorageService azureVodafoneStorageService,
                                     AzureIngestStorageService azureIngestStorageService,
                                     AzureFinalStorageService azureFinalStorageService,
                                     FfmpegService ffmpegService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.mediaServiceBroker = mediaServiceBroker;
        this.recordingService = recordingService;
        this.captureSessionService = captureSessionService;
        this.migrationRecordRepository = migrationRecordRepository;
        this.azureVodafoneStorageService = azureVodafoneStorageService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.ffmpegService = ffmpegService;
    }

    @Override
    public void run() {
        signInRobotUser();
        IMediaService mediaService = mediaServiceBroker.getEnabledMediaService();
        List<RecordingDTO> recordings = recordingService.findAllVodafoneRootRecordings();
        reportItems = new ArrayList<>();

        log.info("Starting BatchFixVodafoneAudioSync for {} Vodafone recording(s)", recordings.size());

        if (recordings.isEmpty()) {
            log.info("No Vodafone recordings found for audio sync repair");
            return;
        }

        long startTime = System.currentTimeMillis();
        for (int index = 0; index < recordings.size(); index++) {
            RecordingDTO recording = recordings.get(index);
            logProgress(index, recordings.size(), startTime, recording);

            try {
                repairRecording(recording, mediaService);
            } catch (Exception e) {
                String message = "Failed to repair Vodafone audio sync";
                log.error("{} for recording {}", message, recording.getId(), e);
                addFailure(recording, message + ": " + e.getMessage());
            }
        }

        writeCsvReport();
        log.info("BatchFixVodafoneAudioSync completed. Successes: {}, Failures: {}",
                 reportItems.stream().filter(item -> item.migrationStatus == RecordingStatus.RECORDING_AVAILABLE).count(),
                 reportItems.stream().filter(item -> item.migrationStatus == RecordingStatus.FAILURE).count());
    }

    @Async
    public void asyncRun() {
        try {
            run();
        } catch (RuntimeException e) {
            log.error("Error while repairing Vodafone audio sync", e);
        }
    }

    private void repairRecording(RecordingDTO recording, IMediaService mediaService) {
        MigrationRecord migrationRecord = findMigrationRecord(recording)
            .orElseThrow(() -> new IllegalStateException(
                "Could not locate vf_migration_records row for recording " + recording.getId()
            ));

        String sourceBlobName = Objects.requireNonNullElse(migrationRecord.getFileName(), "").trim();
        if (sourceBlobName.isEmpty()) {
            throw new IllegalStateException("Migration record does not include an MP4 blob path");
        }

        String repairedBlobName = getRepairedBlobName(sourceBlobName);
        String ingestContainer = recording.getId() + "-input";

        if (!azureIngestStorageService.doesBlobExist(ingestContainer, repairedBlobName)) {
            restoreSourceBlobIfMissing(ingestContainer, sourceBlobName);
            log.info("Running keyframe trim for recording {} from {} to {}",
                     recording.getId(), sourceBlobName, repairedBlobName);
            ffmpegService.trimToSecondKeyframeInIngest(recording.getId(), sourceBlobName, repairedBlobName);
        } else {
            log.info("Repaired blob already exists for recording {} at {}. Reusing it.", recording.getId(),
                     repairedBlobName);
        }

        ensureAssetsExist(recording, mediaService);

        String jobName = mediaService.triggerProcessingStep2(recording.getId(), true, repairedBlobName);
        if (jobName == null) {
            throw new IllegalStateException("EncodeFromMp4 job was not created");
        }

        awaitJobComplete(jobName, mediaService);
        updateRecordingFromFinalStorage(recording, mediaService);
    }

    private Optional<MigrationRecord> findMigrationRecord(RecordingDTO recording) {
        return migrationRecordRepository.findTopByRecordingIdOrderByCreatedAtDesc(recording.getId())
            .or(() -> migrationRecordRepository.findTopByCaptureSessionIdOrderByCreatedAtDesc(
                recording.getCaptureSession().getId()
            ));
    }

    private void restoreSourceBlobIfMissing(String ingestContainer, String sourceBlobName) {
        if (azureIngestStorageService.doesBlobExist(ingestContainer, sourceBlobName)) {
            log.info("Source blob already present in ingest container {} at {}", ingestContainer, sourceBlobName);
            return;
        }

        log.info("Restoring missing ingest blob {} into container {} from Vodafone storage", sourceBlobName,
                 ingestContainer);
        azureIngestStorageService.copyBlobOverwritable(
            ingestContainer,
            sourceBlobName,
            azureVodafoneStorageService.getBlobUrlForCopy(vfSourceContainer, sourceBlobName),
            false
        );
    }

    private void ensureAssetsExist(RecordingDTO recording, IMediaService mediaService) {
        String baseAssetName = recording.getId().toString().replace("-", "");
        String tempAssetName = baseAssetName + "_temp";
        String finalAssetName = baseAssetName + "_output";

        if (mediaService.getAsset(tempAssetName) == null && !mediaService.importAsset(recording, false)) {
            throw new IllegalStateException("Failed to create temporary asset");
        }

        if (mediaService.getAsset(finalAssetName) == null && !mediaService.importAsset(recording, true)) {
            throw new IllegalStateException("Failed to create final asset");
        }
    }

    private void awaitJobComplete(String jobName, IMediaService mediaService) {
        long sleptFor = 0L;
        while (sleptFor < maxSleepTime) {
            RecordingStatus status = mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName);
            if (status == RecordingStatus.RECORDING_AVAILABLE) {
                log.info("EncodeFromMp4 completed for job {}", jobName);
                return;
            }
            if (status == RecordingStatus.FAILURE) {
                throw new IllegalStateException("EncodeFromMp4 failed for job " + jobName);
            }

            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for EncodeFromMp4 job " + jobName, e);
            }
            sleptFor += pollInterval;
        }

        throw new IllegalStateException("Timed out waiting for EncodeFromMp4 job " + jobName);
    }

    private void updateRecordingFromFinalStorage(RecordingDTO recording, IMediaService mediaService) {
        if (!mediaService.verifyFinalAssetExists(recording.getId()).equals(RecordingStatus.RECORDING_AVAILABLE)) {
            throw new IllegalStateException("Final asset not found for recording after transform job");
        }

        String mp4FileName = azureFinalStorageService.getMp4FileName(recording.getId().toString());
        Duration duration = azureFinalStorageService.getRecordingDuration(recording.getId());
        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO(recording);
        createRecordingDTO.setFilename(mp4FileName);
        createRecordingDTO.setDuration(duration);
        recordingService.upsert(createRecordingDTO);

        CreateCaptureSessionDTO captureSessionDTO = new CreateCaptureSessionDTO(recording.getCaptureSession());
        captureSessionDTO.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSessionService.upsert(captureSessionDTO);

        addSuccess(recording.getId(), recording.getCaseReference(), mp4FileName, duration);
    }

    private String getRepairedBlobName(String sourceBlobName) {
        if (sourceBlobName.endsWith(REPAIRED_SUFFIX + ".mp4")) {
            return sourceBlobName;
        }

        int suffixIndex = sourceBlobName.toLowerCase().lastIndexOf(".mp4");
        if (suffixIndex < 0) {
            throw new IllegalStateException("Source blob is not an MP4 file: " + sourceBlobName);
        }

        return sourceBlobName.substring(0, suffixIndex) + REPAIRED_SUFFIX + sourceBlobName.substring(suffixIndex);
    }

    private void logProgress(int index, int total, long startTime, RecordingDTO recording) {
        int processed = index;
        long elapsedMillis = System.currentTimeMillis() - startTime;
        String remaining = "unknown";
        if (processed > 0) {
            long averageMillis = elapsedMillis / processed;
            remaining = formatDuration(Duration.ofMillis((long) (averageMillis * (total - processed))));
        }

        log.info(
            "Audio sync progress {}/{} for recording {} capture session {} (elapsed {}, estimated remaining {})",
            index + 1,
            total,
            recording.getId(),
            recording.getCaptureSession().getId(),
            formatDuration(Duration.ofMillis(elapsedMillis)),
            remaining
        );
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainderSeconds = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, remainderSeconds);
    }

    private void addFailure(RecordingDTO recording, String errorMessage) {
        reportItems.add(new ReportItem(
            recording.getId().toString(),
            recording.getCaseReference(),
            recording.getFilename(),
            recording.getDuration(),
            RecordingStatus.FAILURE,
            errorMessage
        ));
    }

    private void addSuccess(java.util.UUID recordingId, String caseReference, String filename, Duration duration) {
        reportItems.add(new ReportItem(
            recordingId.toString(),
            caseReference,
            filename,
            duration,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        ));
    }

    private void writeCsvReport() {
        if (reportItems.isEmpty()) {
            log.info("No audio sync report created");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "audio_sync_fix_report_" + timestamp + ".csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("RecordingId,CaseReference,Filename,Duration,MigrationStatus,ErrorMessage");

            for (ReportItem item : reportItems) {
                writer.printf(
                    "%s,%s,%s,%d,%s,%s%n",
                    item.recordingId,
                    item.caseReference,
                    item.filename != null ? item.filename : "",
                    item.duration != null ? item.duration.getSeconds() : 0,
                    item.migrationStatus,
                    item.errorMessage != null ? item.errorMessage.replace(",", " ") : ""
                );
            }

            azureFinalStorageService.uploadBlob(filename, REPORT_CONTAINER, filename);
            log.info("Audio sync report written to {}", filename);
        } catch (IOException e) {
            log.error("Failed to write audio sync report CSV", e);
        }
    }

    @AllArgsConstructor
    private static class ReportItem {
        private String recordingId;
        private String caseReference;
        private String filename;
        private Duration duration;
        private RecordingStatus migrationStatus;
        private String errorMessage;
    }
}
