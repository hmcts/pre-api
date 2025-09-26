package uk.gov.hmcts.reform.preapi.tasks.migration;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.tasks.RobotUserTask;
import uk.gov.hmcts.reform.preapi.utils.Batcher;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BatchImportMissingMkAssets extends RobotUserTask {
    private final MediaServiceBroker mediaServiceBroker;
    private final RecordingService recordingService;
    private final CaptureSessionService captureSessionService;
    private final AzureVodafoneStorageService azureVodafoneStorageService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;

    @Value("${tasks.batch-import-missing-mk-assets.batch-size:}")
    private int batchSize;

    @Value("${tasks.batch-import-missing-mk-assets.poll-interval}")
    private long pollInterval;

    @Value("${tasks.batch-import-missing-mk-assets.mp4-source-container}")
    private String vfSourceContainer;

    private List<ReportItem> reportItems;

    @Autowired
    public BatchImportMissingMkAssets(UserService userService,
                                      UserAuthenticationService userAuthenticationService,
                                      @Value("${vodafone-user-email}") String cronUserEmail,
                                      MediaServiceBroker mediaServiceBroker,
                                      RecordingService recordingService,
                                      CaptureSessionService captureSessionService,
                                      AzureVodafoneStorageService azureVodafoneStorageService,
                                      AzureIngestStorageService azureIngestStorageService,
                                      AzureFinalStorageService azureFinalStorageService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.mediaServiceBroker = mediaServiceBroker;
        this.recordingService = recordingService;
        this.azureVodafoneStorageService = azureVodafoneStorageService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.captureSessionService = captureSessionService;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Starting BatchImportMissingMkAssets...");
        reportItems = new ArrayList<>();
        IMediaService mediaService = mediaServiceBroker.getEnabledMediaService();

        // Step 1: Find all VF recordings missing final asset
        List<RecordingDTO> recordings = recordingService.findAllVodafoneRecordings();
        Map<String, RecordingDTO> recordingsMap = recordings.stream()
            .collect(Collectors.toMap(r -> r.getId()
                .toString()
                .replace("-", ""), r -> r));

        if (recordings.isEmpty()) {
            log.info("No recordings missing asset found");
            writeCsvReport();
            log.info("BatchImportMissingMkAssets completed");
            return;
        }
        log.info("Found {} recording(s) missing asset", recordings.size());

        Batcher.batchProcessFunc(
            recordings,
            batchSize,
            // Step 5: Start jobs for batch processing
            recording -> startTransformJob(recording, mediaService),
            jobs -> {
                // Step 6: Await batch completion
                awaitBatchComplete(jobs, mediaService);
                // Step 7: Update recordings with mp4 filename and duration
                jobs.forEach(job -> {
                    var r = recordingsMap.get(job.split("_")[0]);
                    if (r == null) {
                        log.error("recording not found for job: {}", job);
                        return;
                    }
                    updateRecording(r, mediaService);
                });
            }
        );

        // Step 8: Write a report to csv file, saved to blob storage
        writeCsvReport();

        log.info("BatchImportMissingMkAssets completed");
    }

    @Async
    public void asyncRun() {
        try {
            run();
        } catch (RuntimeException e) {
            log.error("Error while batch importing assets", e);
        }
    }

    private boolean copyBlobBetweenContainers(RecordingDTO recording) {
        String destinationContainer = recording.getId().toString() + "-input";
        String blobName = recording.getFilename();

        try {
            azureIngestStorageService.copyBlob(
                destinationContainer,
                blobName,
                azureVodafoneStorageService.getBlobUrlForCopy(vfSourceContainer, blobName)
            );
            return true;
        } catch (Exception e) {
            String message = String.format(
                "Failed to copy blob '%s' between containers: %s -> %s",
                blobName,
                azureVodafoneStorageService.getStorageAccountName() + "/" + vfSourceContainer,
                azureIngestStorageService.getStorageAccountName() + "/" + destinationContainer);
            log.error(message, e);
            addFailure(recording, message);
            return false;
        }
    }

    private String startTransformJob(RecordingDTO recording, IMediaService mediaService) {
        log.info("Starting transform for recording: {}", recording.getId());
        return mediaService.triggerProcessingStep2(recording.getId(), true);
    }

    private void awaitBatchComplete(List<String> jobNames, IMediaService mediaService) {
        log.info("Waiting for transform jobs to complete for batch...");
        List<String> jobs = List.copyOf(jobNames);
        do {
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            jobs = jobs.stream()
                .filter(jobName ->
                            mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, jobName)
                                .equals(RecordingStatus.PROCESSING))
                .toList();
        } while (!jobs.isEmpty());
        log.info("Transform jobs completed for batch");
    }

    private void updateRecording(RecordingDTO recording, IMediaService mediaService) {
        if (!mediaService.verifyFinalAssetExists(recording.getId()).equals(RecordingStatus.RECORDING_AVAILABLE)) {
            log.error("Final asset not found for recording: {}", recording.getId());
            addFailure(recording, "Final asset not found for recording after transform job");
            return;
        }
        updateRecording(
            recording,
            azureFinalStorageService.getMp4FileName(recording.getId().toString()),
            azureFinalStorageService.getRecordingDuration(recording.getId())
        );
    }

    private void updateRecording(RecordingDTO originalRecording, String mp4FileName, Duration duration) {
        if (originalRecording.getFilename().equals(mp4FileName)
            && Objects.equals(originalRecording.getDuration(), duration)) {
            updateCaptureSessionToAvailable(originalRecording.getCaptureSession());
            log.info("Recording id: {} | Original duration: {} | MK duration: {}",
                     originalRecording.getId(),
                     originalRecording.getDuration(),
                     duration);
            addSuccess(originalRecording);
            return;
        }

        CreateRecordingDTO createRecordingDTO = new CreateRecordingDTO(originalRecording);
        if (!originalRecording.getFilename().equals(mp4FileName)) {
            log.info("Updating recording {} with new filename: {}", originalRecording.getId(), mp4FileName);
            createRecordingDTO.setFilename(mp4FileName);
        }

        if (originalRecording.getDuration() == null || !originalRecording.getDuration().equals(duration)) {
            log.info("Updating recording {} with new duration: {}", originalRecording.getId(), duration.toString());
            createRecordingDTO.setDuration(duration);
        }

        recordingService.upsert(createRecordingDTO);
        updateCaptureSessionToAvailable(originalRecording.getCaptureSession());
        addSuccess(createRecordingDTO, originalRecording.getCaseReference());
    }

    private void updateCaptureSessionToAvailable(final CaptureSessionDTO captureSessionDTO) {
        final CreateCaptureSessionDTO dto = new CreateCaptureSessionDTO(captureSessionDTO);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        captureSessionService.upsert(dto);
    }

    protected void addFailure(RecordingDTO recording, String errorMessage) {
        log.info("Adding failure for recording: {}", recording.getId());
        reportItems.add(new ReportItem(
            recording.getId(),
            recording.getCaseReference(),
            recording.getFilename(),
            recording.getDuration(),
            RecordingStatus.FAILURE,
            errorMessage
        ));
    }

    protected void addSuccess(RecordingDTO recording) {
        log.info("Adding success for recording (already matching filename and duration): {}", recording.getId());

        reportItems.add(new ReportItem(
            recording.getId(),
            recording.getCaseReference(),
            recording.getFilename(),
            recording.getDuration(),
            RecordingStatus.RECORDING_AVAILABLE,
            null
        ));
    }

    private void addSuccess(CreateRecordingDTO recording, String caseReference) {
        log.info("Adding success for recording: {}", recording.getId());

        reportItems.add(new ReportItem(
            recording.getId(),
            caseReference,
            recording.getFilename(),
            recording.getDuration(),
            RecordingStatus.RECORDING_AVAILABLE,
            null
        ));
    }

    protected void writeCsvReport() {
        if (reportItems.isEmpty()) {
            log.info("No report created");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "migration_report_" + timestamp + ".csv";

        try {
            PrintWriter writer = new PrintWriter(new FileWriter(filename));
            writer.println("RecordingId,CaseReference,Filename,Duration,MigrationStatus,ErrorMessage");

            for (ReportItem item : reportItems) {
                String line = String.format(
                    "%s,%s,%s,%d,%s,%s",
                    item.recordingId,
                    item.caseReference,
                    item.filename != null ? item.filename : "",
                    item.duration != null ? item.duration.getSeconds() : 0,
                    item.migrationStatus.toString(),
                    item.errorMessage != null ? item.errorMessage.replace(",", " ") : ""
                );
                writer.println(line);
            }

            log.info("CSV report written to {}", filename);
            writer.close();

            azureFinalStorageService.uploadBlob(filename, "mk-import-reports", filename);
        } catch (IOException e) {
            log.error("Failed to write migration report CSV", e);
        }
    }

    @AllArgsConstructor
    protected static class ReportItem {
        private UUID recordingId;
        private String caseReference;
        private String filename;
        private Duration duration;
        private RecordingStatus migrationStatus;
        private String errorMessage;
    }
}
