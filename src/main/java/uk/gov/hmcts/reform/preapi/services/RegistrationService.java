package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.dto.MkJob;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static uk.gov.hmcts.reform.preapi.media.MediaKind.ENCODE_FROM_INGEST_TRANSFORM;
import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getSanitisedLiveEventId;

@Slf4j
@Service
public class RegistrationService {

    public static final Timestamp PROCESSING_TIMEOUT = Timestamp.from(Instant.now()
                                                                          .minus(2, ChronoUnit.HOURS));

    private static final Integer EXPECTED_MAX_NUMBER_OF_JOBS_PER_CS = 2;

    private final CaptureSessionService captureSessionService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final IMediaService mediaService;

    protected RegistrationService(final CaptureSessionService captureSessionService,
                                  final MediaServiceBroker mediaServiceBroker,
                                  final AzureIngestStorageService azureIngestStorageService) {
        this.captureSessionService = captureSessionService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.mediaService = mediaServiceBroker.getEnabledMediaService();
    }

    public UpsertResult register(UUID captureSessionId) {
        String liveEventId = getSanitisedLiveEventId(captureSessionId);

        LiveEventDTO liveEvent;
        try {
            liveEvent = mediaService.getLiveEvent(liveEventId);
            // Should be null if live event has finished
            if (liveEvent != null) {
                String errorMessage = format(
                    "Capture session %s cannot be deleted: live event still exists and is in state %s",
                    captureSessionId, liveEvent.getResourceState()
                );
                throw new ResourceInWrongStateException(errorMessage);
            }
        } catch (NotFoundException e) {
            // this was expected. I really don't like doing this. Is there a better way?
        }

        MkJob encodeFromIngestJob = mediaService.getJobFromPartialName(
            ENCODE_FROM_INGEST_TRANSFORM,
            liveEventId
        );

        if (encodeFromIngestJob.getProperties().getState() != JobState.FINISHED) {
            throw new ResourceInWrongStateException(format(
                "Capture session %s cannot be deleted: ingest job is not finished",
                captureSessionId
            ));
        }

        List<JobOutputAsset> jobOutputAssets = getJobOutputAssets(captureSessionId, encodeFromIngestJob);
        AssetDTO asset = mediaService.getAsset(jobOutputAssets.getFirst().assetName());

        UUID recordingId = UUID.fromString(asset.getContainer());
        RecordingStatus recordingStatusOfFinalAsset =
            mediaService.verifyFinalAssetExists(recordingId);

        if (recordingStatusOfFinalAsset != RecordingStatus.RECORDING_AVAILABLE) {
            throw new ResourceInWrongStateException(format(
                "Capture session %s cannot be registered: recording %s is "
                    + "not available in storage", captureSessionId, recordingId
            ));
        }

        log.info("Found a recording for capture session {}", captureSessionId);

        CaptureSessionDTO captureSession = captureSessionService.stopCaptureSession(
            captureSessionId, RecordingStatus.RECORDING_AVAILABLE, recordingId);

        azureIngestStorageService.markContainerAsSafeToDelete(captureSession.getBookingId().toString());
        azureIngestStorageService.markContainerAsSafeToDelete(recordingId.toString());

        return UpsertResult.UPDATED;
    }

    private static @NotNull List<JobOutputAsset> getJobOutputAssets(UUID captureSessionId, MkJob encodeFromIngestJob) {
        List<JobOutputAsset> jobOutputAssets =
            encodeFromIngestJob.getProperties() != null && encodeFromIngestJob.getProperties().getOutputs() != null
                ? encodeFromIngestJob.getProperties().getOutputs()
                : List.of();

        if (jobOutputAssets.isEmpty()) {
            String errorMessage = format(
                "Capture session %s cannot be deleted: found no output assets from ingest job",
                captureSessionId
            );
            throw new ResourceInWrongStateException(errorMessage);
        }
        return jobOutputAssets;
    }
}
