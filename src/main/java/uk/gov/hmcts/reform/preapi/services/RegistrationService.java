package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import com.azure.resourcemanager.mediaservices.models.JobState;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.dto.MkJob;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

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

    private final CaptureSessionService captureSessionService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final IMediaService mediaService;
    private final RecordingService recordingService;
    private final Integer processingTimeoutMinutes;

    protected RegistrationService(final CaptureSessionService captureSessionService,
                                  final MediaServiceBroker mediaServiceBroker,
                                  final AzureIngestStorageService azureIngestStorageService,
                                  @Value("${capture-session-registration.processing-timeout-minutes}")
                                  final Integer processingTimeoutMinutes,
                                  final RecordingService recordingService) {
        this.captureSessionService = captureSessionService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.mediaService = mediaServiceBroker.getEnabledMediaService();
        this.processingTimeoutMinutes = processingTimeoutMinutes;
        this.recordingService = recordingService;
    }

    /**
     * Deals with capture sessions in a non-processed state, e.g.
     * - MediaKind finished processing but the recording was not registered on PRE database
     * - Something interrupted the MediaKind processing jobs
     *
     * <ul>
     *   <li>Check live event has finished (MediaKind returns NotFound).</li>
     *   <li>Verify whether EncodeFromIngest (first) job has completed --> Register recording</li>
     *   <li>Otherwise try to process the capture session from scratch.</li>
     * </ul>
     * Assumes the EncodeFromIngest and EncodeFromMp4 asset containers share a UUID
     *
     * @param captureSessionId The capture session ID for a session that is still in the processing state
     *                         but needs to be registered as complete.
     * @param forceReprocess If true, will delete any existing recordings for the capture session
     *                       and reprocess from scratch, even if a previous encoding job has completed successfully.
     * @return {@link UpsertResult#UPDATED} if the capture session was successfully registered.
     */
    public UpsertResult register(UUID captureSessionId, boolean forceReprocess) throws InterruptedException {
        final String liveEventId = getSanitisedLiveEventId(captureSessionId);

        verifyCaptureSessionIsPresentAndReadyForReprocessing(captureSessionId, liveEventId);

        log.info("Begin registering recording for capture session {}", captureSessionId);

        MkJob encodeFromIngestJob = mediaService.getJobFromPartialName(
            ENCODE_FROM_INGEST_TRANSFORM,
            liveEventId
        );

        if (!forceReprocess
            && encodeFromIngestJob != null
            && encodeFromIngestJob.getProperties().getState() == JobState.FINISHED) {
            log.info("Capture session {} has already been processed. Attempting to register...", captureSessionId);
            if (registerEncodedRecording(captureSessionId, encodeFromIngestJob) == UpsertResult.UPDATED) {
                return UpsertResult.UPDATED;
            }
            // If unsuccessful, fall through to reprocess the capture session from scratch
        }

        log.info("Finding and deleting any previous encodings for capture session {}", captureSessionId);
        SearchRecordings params = new SearchRecordings();
        params.setCaptureSessionId(captureSessionId);
        recordingService.findAll(params, false, Pageable.unpaged()).forEach(recording -> {
            log.info("Deleting recording {} for capture session {}", recording.getId(), captureSessionId);
            recordingService.deleteById(recording.getId());
        });

        log.info("Creating new recording for capture session {}", captureSessionId);
        UUID recordingId = UUID.randomUUID();
        RecordingStatus recordingStatus = mediaService.processCaptureSession(
            captureSessionService.findById(captureSessionId),
            recordingId, liveEventId
        );

        if (recordingStatus == RecordingStatus.FAILURE) {
            captureSessionService.stopCaptureSession(captureSessionId, RecordingStatus.FAILURE, null);
            throw new UnknownServerException("Encountered an error during encoding process for CaptureSession("
                                                 + captureSessionId
                                                 + ")");
        }
        captureSessionService.stopCaptureSession(captureSessionId, recordingStatus, recordingId);

        return UpsertResult.UPDATED;
    }

    private UpsertResult registerEncodedRecording(UUID captureSessionId, MkJob encodeFromIngestJob) {
        List<JobOutputAsset> ingestJobOutputAssets = getJobOutputAssets(encodeFromIngestJob);
        if (ingestJobOutputAssets.isEmpty()) {
            log.info(format(
                "Capture session %s cannot be registered from previous job: found no output assets from ingest job",
                captureSessionId
            ));
            return UpsertResult.UNSUCCESSFUL;
        }

        JobOutputAsset firstOutputAsset = ingestJobOutputAssets.getFirst();
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

    private void verifyCaptureSessionIsPresentAndReadyForReprocessing(UUID captureSessionId,
                                                                      String sanitisedLiveEventId) {
        CaptureSessionDTO inDatabase;
        try {
            inDatabase = captureSessionService.findById(captureSessionId);
        } catch (NotFoundException e) {
            throw new BadRequestException(format(
                "Capture Session with id %s not on the PRE system at all. Typo?",
                captureSessionId
            ), e);
        }

        if (inDatabase.getStatus() != RecordingStatus.PROCESSING
            && inDatabase.getStatus() != RecordingStatus.NO_RECORDING
            && inDatabase.getStatus() != RecordingStatus.FAILURE) {
            throw new ResourceInWrongStateException(
                format(
                    "Capture session with ID %s is in an incorrect state for registration: %s",
                    captureSessionId, inDatabase.getStatus()
                ));
        }

        Timestamp timestampAtEndOfWindowOfTolerance = Timestamp.from(inDatabase.getFinishedAt().toInstant()
                                                                         .plus(
                                                                             processingTimeoutMinutes,
                                                                             ChronoUnit.MINUTES
                                                                         ));

        if (timestampAtEndOfWindowOfTolerance.after(Timestamp.from(Instant.now()))) {
            String errorMessage = format(
                "Capture session with ID %s finished processing at %s on %s. "
                    + "This is within the agreed tolerance window of %s minutes."
                    + " Please try again after %s.",
                captureSessionId,
                DateTimeUtils.formatTime(inDatabase.getFinishedAt()),
                DateTimeUtils.formatDate(inDatabase.getFinishedAt()),
                processingTimeoutMinutes,
                DateTimeUtils.formatTime(timestampAtEndOfWindowOfTolerance)
            );

            throw new ResourceInWrongStateException(errorMessage);
        }

        mediaService.validateLiveEventIsFinished(sanitisedLiveEventId, captureSessionId);
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
