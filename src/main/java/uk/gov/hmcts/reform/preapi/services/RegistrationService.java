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

    /**
     * Deals with capture sessions that have finished processing and have a recording in storage but the database does
     * not reflect that yet.
     * <ul>
     *  <li>Checks that the live event for the session is finished (NotFound from Media Kind)</li>
     *  <li>Gets the EncodeFromIngest job and checks it is finished</li>
     *  <li>Checks the recording is available in storage and updates capture session and recording databases</li>
     * </ul>
     * Assumptions:
     * <ul>
     *  <li>Assumes the EncodeFromIngest job asset container
     *  has the same UUID name as the EncodeFromMp4 asset container.</li>
     * </ul>
     * @param captureSessionId - the capture session that is still in processing state and needs to be registered</li>
     * @return UpsertResult.UPDATED if the capture session was successfully registered
     */
    public UpsertResult register(UUID captureSessionId) {
        log.info("Begin registering recording for capture session {}", captureSessionId);

        String liveEventId = getSanitisedLiveEventId(captureSessionId);

        LiveEventDTO liveEvent;
        try {
            liveEvent = mediaService.getLiveEvent(liveEventId);
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
