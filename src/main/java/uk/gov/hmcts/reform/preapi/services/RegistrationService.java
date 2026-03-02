package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
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

    /**
     * Deals with capture sessions that have finished processing and have a recording stored,
     * but whose database state has not yet been updated to reflect that. Steps:
     *
     * <ul>
     *   <li>Check live event has finished (MediaKind returns NotFound).</li>
     *   <li>Verify EncodeFromIngest job has completed.</li>
     *   <li>Check that the recording is available in final storage and update the relevant
     *       capture session and recording database items.</li>
     * </ul>
     * Assumes the EncodeFromIngest and EncodeFromMp4 asset containers share a UUID.
     * @param captureSessionId
     *        The capture session ID for a session that is still in the processing state
     *        but needs to be registered as complete.
     *
     * @return {@link UpsertResult#UPDATED} if the capture session was successfully registered.
     */
    public UpsertResult register(UUID captureSessionId) {
        log.info("Begin registering recording for capture session {}", captureSessionId);

        String liveEventId = getSanitisedLiveEventId(captureSessionId);

        validateLiveEventIsFinished(liveEventId, captureSessionId);

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

        List<JobOutputAsset> ingestJobOutputAssets = getJobOutputAssets(encodeFromIngestJob);
        if (ingestJobOutputAssets.isEmpty()) {
            String errorMessage = format(
                    "Capture session %s cannot be deleted: found no output assets from ingest job",
                    captureSessionId
            );
            throw new ResourceInWrongStateException(errorMessage);
        }

        JobOutputAsset firstOutputAsset = ingestJobOutputAssets.getFirst();
        if (firstOutputAsset == null) {
            throw new ResourceInWrongStateException(
                    format("Capture session %s cannot be registered: no output asset found for job %s",
                            captureSessionId, encodeFromIngestJob.getName())
            );
        }
        UUID recordingId = UUID.fromString(mediaService.getAsset(firstOutputAsset.assetName()).getContainer());
        verifyFinalAssetIsAvailable(recordingId, captureSessionId);

        log.info("Found a recording for capture session {}", captureSessionId);

        CaptureSessionDTO captureSession = captureSessionService.stopCaptureSession(
            captureSessionId, RecordingStatus.RECORDING_AVAILABLE, recordingId);

        azureIngestStorageService.markContainerAsSafeToDelete(captureSession.getBookingId().toString());
        azureIngestStorageService.markContainerAsSafeToDelete(recordingId.toString());

        log.info("Capture session {} registered successfully.", captureSessionId);
        return UpsertResult.UPDATED;
    }

    private void validateLiveEventIsFinished(String sanitisedCaptureSessionId, UUID captureSessionId) {
        try {
            LiveEventDTO liveEvent = mediaService.getLiveEvent(sanitisedCaptureSessionId);
            if (liveEvent != null) {
                String errorMessage = format(
                        "Capture session %s cannot be deleted: live event still exists and is in state %s",
                        captureSessionId, liveEvent.getResourceState()
                );
                throw new ResourceInWrongStateException(errorMessage);
            }
        } catch (NotFoundException e) {
            log.info("Live event not found for capture session {}:"
                    + " expected if live event is finished", captureSessionId);
        }
    }

    private void verifyFinalAssetIsAvailable(UUID recordingId, UUID captureSessionId) {
        RecordingStatus recordingStatusOfFinalAsset =
            mediaService.verifyFinalAssetExists(recordingId);

        if (recordingStatusOfFinalAsset != RecordingStatus.RECORDING_AVAILABLE) {
            throw new ResourceInWrongStateException(format(
                "Capture session %s cannot be registered: recording %s is "
                    + "not available in storage", captureSessionId, recordingId
            ));
        }
    }

    private static @NotNull List<JobOutputAsset> getJobOutputAssets(MkJob encodeFromIngestJob) {
        return encodeFromIngestJob.getProperties() != null && encodeFromIngestJob.getProperties().getOutputs() != null
            ? encodeFromIngestJob.getProperties().getOutputs()
            : List.of();
    }
}
