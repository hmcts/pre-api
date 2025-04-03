package uk.gov.hmcts.reform.preapi.tasks.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.tasks.RobotUserTask;
import uk.gov.hmcts.reform.preapi.util.Batcher;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class BatchImportMissingMkAssets extends RobotUserTask {
    private final MediaServiceBroker mediaServiceBroker;
    private final RecordingService recordingService;
    private final AzureVodafoneStorageService azureVodafoneStorageService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;

    @Value("${tasks.batch-import-missing-mk-assets.batch-size:}")
    private int batchSize;

    @Value("${tasks.batch-import-missing-mk-assets.poll-interval}")
    private long pollInterval;

    @Value("${tasks.batch-import-missing-mk-assets.mp4-source-container}")
    private String vfSourceContainer;

    @Autowired
    public BatchImportMissingMkAssets(UserService userService,
                                      UserAuthenticationService userAuthenticationService,
                                      @Value("${cron-user-email}") String cronUserEmail,
                                      MediaServiceBroker mediaServiceBroker,
                                      RecordingService recordingService,
                                      AzureVodafoneStorageService azureVodafoneStorageService,
                                      AzureIngestStorageService azureIngestStorageService,
                                      AzureFinalStorageService azureFinalStorageService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.mediaServiceBroker = mediaServiceBroker;
        this.recordingService = recordingService;
        this.azureVodafoneStorageService = azureVodafoneStorageService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Starting BatchImportMissingMkAssets...");
        IMediaService mediaService = mediaServiceBroker.getEnabledMediaService();

        // Step 1: Find all VF recordings missing final asset
        List<RecordingDTO> recordings = recordingService.findAllVodafoneRecordings().stream()
            .filter(recording ->
                        mediaService.getAsset(recording.getId().toString().replace("-", "") + "_output") == null)
            // Step 2: Copy blob from Vodafone to Ingest
            .filter(this::copyBlobBetweenContainers)
            // Step 3: Create Temp Asset
            .filter(recording ->  mediaService.importAsset(recording, false))
            // Step 4: Create Final Asset
            .filter(recording -> mediaService.importAsset(recording, true))
            .toList();

        if (recordings.isEmpty()) {
            log.info("No recordings missing asset found");
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
                recordings.forEach(recording -> updateRecording(recording, mediaService));
            }
        );

        log.info("BatchImportMissingMkAssets completed");
    }

    private boolean copyBlobBetweenContainers(RecordingDTO recording) {
        String destinationContainer = recording.getId().toString() + "-input";
        String blobName = recording.getFilename();

        try {
            azureIngestStorageService.copyBlob(
                destinationContainer,
                blobName,
                azureVodafoneStorageService.getBlobUrlWithSasForCopy(vfSourceContainer, blobName)
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to copy blob '{}' between containers: {} -> {}",
                      blobName,
                      azureVodafoneStorageService.getStorageAccountName() + "/" + vfSourceContainer,
                      azureIngestStorageService.getStorageAccountName() + "/" + destinationContainer,
                      e);
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
    }
}
