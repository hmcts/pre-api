package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobOutputAsset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
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

        String ingestJobName = format("%s_temp", liveEventId);
        RecordingStatus ingestJobStatus = mediaService.hasJobCompleted(
            MediaKind.ENCODE_FROM_INGEST_TRANSFORM,
            ingestJobName
        );
        // Should be null if live event has finished
        if (ingestJobStatus != RecordingStatus.RECORDING_AVAILABLE) {
            String errorMessage = format(
                "Capture session %s cannot be deleted: ingest job status is %s",
                captureSessionId, ingestJobStatus
            );
            throw new ResourceInWrongStateException(errorMessage);
        }

        List<JobOutputAsset> jobOutputAsset = mediaService.getJobOutputAssets(
            MediaKind.ENCODE_FROM_INGEST_TRANSFORM,
            ingestJobName
        );

        if (jobOutputAsset.isEmpty()) {
            String errorMessage = format(
                "Capture session %s cannot be deleted: found no output assets from ingest job",
                captureSessionId
            );
            throw new ResourceInWrongStateException(errorMessage);
        }

        UUID recordingId = UUID.fromString(jobOutputAsset.getFirst().assetName());
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
}
