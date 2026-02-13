package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.EncodeJobDTO;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;


/**
 * {@summary This belongs to the unused cron job solution for stuck-in-processing capture sessions.}
 *
 * @deprecated This belongs to the cron job solution for the problem where capture sessions get stuck in processing.
 *     See <a href="https://tools.hmcts.net/jira/issues/?jql=labels%20%3D%20capture-session-interrupted-processing">Jira label</a>.
 *     We have decided not to proceed with this solution, and we are investigating other architectural solutions.
 *     See <a href="https://justiceuk.sharepoint.com/:w:/r/sites/PreRecordedEvidenceBAUTeam/Shared%20Documents/General/Spikes/Recording%20Process%20-%20Event%20Based%20Workflow%20Spike.docx?d=wc920d7600b8446c889fd197324e7927c&csf=1&web=1&e=EuSHgB">spike</a>.
 */
@Deprecated
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
