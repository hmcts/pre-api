package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.EncodeJobDTO;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;
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

@Slf4j
@Service
public class ProcessingService {

    public static final Timestamp PROCESSING_TIMEOUT = Timestamp.from(Instant.now()
                                                                   .minus(2, ChronoUnit.HOURS));

    private static final Integer EXPECTED_MAX_NUMBER_OF_JOBS_PER_CS = 2;

    private final CaptureSessionService captureSessionService;
    private final EncodeJobService encodeJobService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final IMediaService mediaService;

    protected ProcessingService(final CaptureSessionService captureSessionService,
                                final MediaServiceBroker mediaServiceBroker,
                                final EncodeJobService encodeJobService,
                                final AzureIngestStorageService azureIngestStorageService) {
        this.captureSessionService = captureSessionService;
        this.encodeJobService = encodeJobService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.mediaService = mediaServiceBroker.getEnabledMediaService();
    }

    public void processAllCaptureSessions() {
        encodeJobService.findAllProcessing().forEach(job -> checkJob(job, mediaService));

        encodeJobService.findAllProcessing()
            .stream()
            .filter(job -> job.getCreatedAt().before(PROCESSING_TIMEOUT)) // 2hrs
            .forEach(job -> {
                log.error(
                    "Processing job {} for capture session {} has timed out",
                    job.getJobName(),
                    job.getCaptureSessionId()
                );
                encodeJobService.delete(job.getId());
                captureSessionService.stopCaptureSession(job.getCaptureSessionId(), RecordingStatus.FAILURE, null);
            });
    }

    public UpsertResult register(UUID captureSessionId) {
        List<EncodeJobDTO> listOfEncodingJobs = encodeJobService.findAllProcessing()
            .stream()
            .filter(job -> job.getCaptureSessionId().equals(captureSessionId))
            .toList();

        if (listOfEncodingJobs.isEmpty()) {
            throw new NotFoundException("Did not find any encoding jobs for capture session: " + captureSessionId);
        }

        if (listOfEncodingJobs.size() > EXPECTED_MAX_NUMBER_OF_JOBS_PER_CS) {
            throw new ResourceInWrongStateException(format(
                "Expected 1 or 2 encoding jobs for capture session: %s. "
                    + "Actually there are %d encoding jobs", captureSessionId,
                listOfEncodingJobs.size()
            ));
        }

        listOfEncodingJobs.stream().spliterator().forEachRemaining(job -> {
            RecordingStatus jobCompletionStatus = mediaService.hasJobCompleted(
                getTransformName(job.getTransform()),
                job.getJobName()
            );

            if (jobCompletionStatus != RecordingStatus.RECORDING_AVAILABLE) {
                throw new ResourceInWrongStateException(format(
                    "Capture session %s cannot be registered: processing job %s "
                        + "status is %s", captureSessionId, job.getTransform().toString(), jobCompletionStatus
                ));
            }
        });

        EncodeJobDTO finalEncodingJob = listOfEncodingJobs.stream()
            .filter(job -> job.getTransform().equals(EncodeTransform.ENCODE_FROM_MP4))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(format(
                "Did not find a final encoding job for Capture Session %s",
                captureSessionId
            )));

        RecordingStatus recordingStatusOfFinalAsset =
            mediaService.verifyFinalAssetExists(finalEncodingJob.getRecordingId());
        if (recordingStatusOfFinalAsset != RecordingStatus.RECORDING_AVAILABLE) {
            throw new ResourceInWrongStateException(format(
                "Capture session %s cannot be registered: recording is "
                    + "not available in storage", captureSessionId
            ));
        }

        log.info("Found a recording for capture session {}", finalEncodingJob.getCaptureSessionId());

        CaptureSessionDTO captureSession = captureSessionService.stopCaptureSession(
            finalEncodingJob.getCaptureSessionId(),
            RecordingStatus.RECORDING_AVAILABLE,
            finalEncodingJob.getRecordingId()
        );

        azureIngestStorageService.markContainerAsSafeToDelete(captureSession.getBookingId().toString());
        azureIngestStorageService.markContainerAsSafeToDelete(finalEncodingJob.getRecordingId().toString());

        return UpsertResult.UPDATED;
    }

    private void checkJob(EncodeJobDTO job, IMediaService mediaService) {
        try {
            switch (mediaService.hasJobCompleted(getTransformName(job.getTransform()), job.getJobName())) {
                case RECORDING_AVAILABLE -> {
                    if (job.getTransform().equals(EncodeTransform.ENCODE_FROM_INGEST)) {
                        onEncodeFromIngestProcessingComplete(job, mediaService);
                    } else {
                        onEncodeFromMp4ProcessingComplete(job, mediaService);
                    }
                }
                case FAILURE -> {
                    log.error(
                        "Processing job {} failed for capture session {}",
                        job.getJobName(),
                        job.getCaptureSessionId()
                    );
                    encodeJobService.delete(job.getId());
                    captureSessionService.stopCaptureSession(
                        job.getCaptureSessionId(),
                        RecordingStatus.FAILURE,
                        null
                    );
                }
                default -> log.info(
                    "Job {} for capture session {} is still processing",
                    job.getJobName(),
                    job.getCaptureSessionId()
                );
            }
        } catch (Exception e) {
            log.error(
                "Failed to check job {} for capture session {}. Marking as FAILURE.",
                job.getJobName(),
                job.getCaptureSessionId(),
                e
            );
            encodeJobService.delete(job.getId());
            captureSessionService.stopCaptureSession(
                job.getCaptureSessionId(),
                RecordingStatus.FAILURE, null
            );
        }
    }

    private void onEncodeFromIngestProcessingComplete(EncodeJobDTO job, IMediaService mediaService) {
        log.info("EncodeFromIngest for capture session {} is complete", job.getCaptureSessionId());
        String jobName = mediaService.triggerProcessingStep2(job.getRecordingId(), false);

        if (jobName == null) {
            log.info("No recording found for capture session {}", job.getCaptureSessionId());
            encodeJobService.delete(job.getId());
            captureSessionService.stopCaptureSession(
                job.getCaptureSessionId(),
                RecordingStatus.NO_RECORDING, null
            );
            return;
        }

        job.setJobName(jobName);
        job.setTransform(EncodeTransform.ENCODE_FROM_MP4);
        encodeJobService.upsert(job);
    }

    private void onEncodeFromMp4ProcessingComplete(EncodeJobDTO job, IMediaService mediaService) {
        log.info("EncodeFromMp4 for capture session {} is complete", job.getCaptureSessionId());
        encodeJobService.delete(job.getId());
        if (mediaService.verifyFinalAssetExists(job.getRecordingId()) == RecordingStatus.RECORDING_AVAILABLE) {
            log.info("Final asset found for capture session {}", job.getCaptureSessionId());
            CaptureSessionDTO captureSession = captureSessionService.stopCaptureSession(
                job.getCaptureSessionId(),
                RecordingStatus.RECORDING_AVAILABLE,
                job.getRecordingId()
            );
            azureIngestStorageService.markContainerAsSafeToDelete(captureSession.getBookingId().toString());
            azureIngestStorageService.markContainerAsSafeToDelete(job.getRecordingId().toString());
        } else {
            log.error("Final asset not found for capture session {}", job.getCaptureSessionId());
            captureSessionService.stopCaptureSession(
                job.getCaptureSessionId(),
                RecordingStatus.FAILURE, null
            );
        }
    }

    private String getTransformName(EncodeTransform transform) {
        return switch (transform) {
            case ENCODE_FROM_INGEST -> MediaKind.ENCODE_FROM_INGEST_TRANSFORM;
            case ENCODE_FROM_MP4 -> MediaKind.ENCODE_FROM_MP4_TRANSFORM;
        };
    }
}
