package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.RecordingReencodeJob;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Service
public class VodafoneRecordingReencodeService {
    private final RecordingRepository recordingRepository;
    private final CaptureSessionRepository captureSessionRepository;
    private final MigrationRecordRepository migrationRecordRepository;
    private final AzureVodafoneStorageService azureVodafoneStorageService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final MediaServiceBroker mediaServiceBroker;
    private final CommandExecutor commandExecutor;
    private final RecordingService recordingService;
    private final CaptureSessionService captureSessionService;
    private final long pollInterval;
    private final long maxSleepTime;

    @Autowired
    public VodafoneRecordingReencodeService(RecordingRepository recordingRepository,
                                            CaptureSessionRepository captureSessionRepository,
                                            MigrationRecordRepository migrationRecordRepository,
                                            AzureVodafoneStorageService azureVodafoneStorageService,
                                            AzureIngestStorageService azureIngestStorageService,
                                            AzureFinalStorageService azureFinalStorageService,
                                            MediaServiceBroker mediaServiceBroker,
                                            CommandExecutor commandExecutor,
                                            RecordingService recordingService,
                                            CaptureSessionService captureSessionService,
                                            @Value("${tasks.vf-recording-reencode.poll-interval}") long pollInterval,
                                            @Value("${tasks.vf-recording-reencode.max-sleep-time}")
                                                long maxSleepTime) {
        this.recordingRepository = recordingRepository;
        this.captureSessionRepository = captureSessionRepository;
        this.migrationRecordRepository = migrationRecordRepository;
        this.azureVodafoneStorageService = azureVodafoneStorageService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.mediaServiceBroker = mediaServiceBroker;
        this.commandExecutor = commandExecutor;
        this.recordingService = recordingService;
        this.captureSessionService = captureSessionService;
        this.pollInterval = pollInterval;
        this.maxSleepTime = maxSleepTime;
    }

    public void processJob(RecordingReencodeJob job) {
        Recording recording = recordingRepository.findByIdAndDeletedAtIsNull(job.getRecordingId())
            .orElseThrow(() -> new NotFoundException("Recording: " + job.getRecordingId()));
        CaptureSession captureSession = captureSessionRepository.findByIdAndDeletedAtIsNull(job.getCaptureSessionId())
            .orElseThrow(() -> new NotFoundException("CaptureSession: " + job.getCaptureSessionId()));
        migrationRecordRepository.findById(job.getMigrationRecordId())
            .orElseThrow(() -> new NotFoundException("MigrationRecord: " + job.getMigrationRecordId()));

        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("vf-recording-reencode-");
            Path inputFilePath = tempDirectory.resolve(job.getSourceBlobName());
            Path outputFilePath = tempDirectory.resolve(job.getReencodedBlobName());

            downloadSource(job, inputFilePath);
            reencode(inputFilePath, outputFilePath, job.getId());
            uploadReencodedSource(job, outputFilePath);
            overwriteIngestBlob(job, recording);

            IMediaService mediaService = mediaServiceBroker.getEnabledMediaService();
            ensureAssetsExist(mediaService, recording);

            String jobName = mediaService.triggerProcessingStep2(recording.getId(), true);
            if (jobName == null) {
                throw new UnknownServerException("Failed to trigger MediaKind reprocessing for recording "
                                                     + recording.getId());
            }

            awaitReprocessing(jobName, mediaService);
            syncDatabase(recording, captureSession, mediaService);
        } catch (IOException e) {
            throw new UnknownServerException("Failed to prepare temporary files for re-encoding", e);
        } finally {
            cleanup(tempDirectory);
        }
    }

    protected CommandLine generateReencodeCommand(Path inputFilePath, Path outputFilePath) {
        return new CommandLine("ffmpeg")
            .addArgument("-y")
            .addArgument("-fflags")
            .addArgument("+genpts")
            .addArgument("-err_detect")
            .addArgument("ignore_err")
            .addArgument("-i")
            .addArgument(inputFilePath.toString(), true)
            .addArgument("-c:v")
            .addArgument("libx264")
            .addArgument("-preset")
            .addArgument("slow")
            .addArgument("-crf")
            .addArgument("21")
            .addArgument("-af")
            .addArgument("aresample=async=1:first_pts=0")
            .addArgument("-c:a")
            .addArgument("aac")
            .addArgument("-b:a")
            .addArgument("128k")
            .addArgument("-movflags")
            .addArgument("+faststart")
            .addArgument(outputFilePath.toString(), true);
    }

    private void downloadSource(RecordingReencodeJob job, Path inputFilePath) {
        if (!azureVodafoneStorageService.downloadBlob(
            job.getSourceContainer(),
            job.getSourceBlobName(),
            inputFilePath.toString()
        )) {
            throw new UnknownServerException("Failed to download source blob "
                                                 + job.getSourceContainer()
                                                 + "/"
                                                 + job.getSourceBlobName());
        }
    }

    private void reencode(Path inputFilePath, Path outputFilePath, UUID jobId) {
        CommandLine commandLine = generateReencodeCommand(inputFilePath, outputFilePath);
        if (!commandExecutor.execute(commandLine)) {
            throw new UnknownServerException("Failed to re-encode recording for job " + jobId);
        }
    }

    private void uploadReencodedSource(RecordingReencodeJob job, Path outputFilePath) {
        if (!azureVodafoneStorageService.uploadBlob(
            outputFilePath.toString(),
            job.getSourceContainer(),
            job.getReencodedBlobName()
        )) {
            throw new UnknownServerException("Failed to upload re-encoded blob "
                                                 + job.getSourceContainer()
                                                 + "/"
                                                 + job.getReencodedBlobName());
        }
    }

    private void overwriteIngestBlob(RecordingReencodeJob job, Recording recording) {
        String sourceUrl = azureVodafoneStorageService.getBlobUrlForCopy(
            job.getSourceContainer(),
            job.getReencodedBlobName()
        );
        azureIngestStorageService.copyBlobOverwritable(
            recording.getId() + "-input",
            recording.getFilename(),
            sourceUrl,
            true
        );
    }

    private void ensureAssetsExist(IMediaService mediaService, Recording recording) {
        String recordingAssetPrefix = recording.getId().toString().replace("-", "");
        RecordingDTO recordingDTO = new RecordingDTO(recording);

        if (mediaService.getAsset(recordingAssetPrefix + "_temp") == null) {
            if (!mediaService.importAsset(recordingDTO, false)) {
                throw new UnknownServerException("Failed to create MediaKind temporary asset for recording "
                                                     + recording.getId());
            }
        }

        if (mediaService.getAsset(recordingAssetPrefix + "_output") == null) {
            if (!mediaService.importAsset(recordingDTO, true)) {
                throw new UnknownServerException("Failed to create MediaKind final asset for recording "
                                                     + recording.getId());
            }
        }
    }

    private void awaitReprocessing(String jobName, IMediaService mediaService) {
        long sleptFor = 0L;
        RecordingStatus status;

        do {
            status = mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName);
            if (status != RecordingStatus.PROCESSING) {
                break;
            }

            try {
                Thread.sleep(pollInterval);
                sleptFor += pollInterval;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UnknownServerException("Interrupted while waiting for MediaKind reprocessing", e);
            }
        } while (sleptFor < maxSleepTime);

        if (status != RecordingStatus.RECORDING_AVAILABLE) {
            throw new UnknownServerException("MediaKind reprocessing did not finish successfully for job " + jobName);
        }
    }

    private void syncDatabase(Recording recording, CaptureSession captureSession, IMediaService mediaService) {
        if (mediaService.verifyFinalAssetExists(recording.getId()) != RecordingStatus.RECORDING_AVAILABLE) {
            throw new UnknownServerException("Final MediaKind asset not available for recording " + recording.getId());
        }

        String finalFilename = azureFinalStorageService.getMp4FileName(recording.getId().toString());
        Duration finalDuration = azureFinalStorageService.getRecordingDuration(recording.getId());
        if (finalDuration == null) {
            throw new UnknownServerException("Duration not found in final storage for recording " + recording.getId());
        }

        CreateRecordingDTO recordingDTO = new CreateRecordingDTO(recording);
        recordingDTO.setFilename(finalFilename);
        recordingDTO.setDuration(finalDuration);
        recordingService.forceUpsert(recordingDTO);

        CreateCaptureSessionDTO captureSessionDTO = new CreateCaptureSessionDTO(new CaptureSessionDTO(captureSession));
        captureSessionDTO.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        captureSessionService.upsert(captureSessionDTO);
    }

    private void cleanup(Path tempDirectory) {
        if (tempDirectory == null) {
            return;
        }

        try (var paths = Files.walk(tempDirectory)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete temp path {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to clean up temp directory {}", tempDirectory, e);
        }
    }
}
