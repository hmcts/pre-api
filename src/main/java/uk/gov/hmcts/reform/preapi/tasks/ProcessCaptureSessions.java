package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.EncodeJobDTO;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.EncodeJobService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.time.Instant;

@Slf4j
@Component
public class ProcessCaptureSessions extends RobotUserTask {
    private final CaptureSessionService captureSessionService;
    private final MediaServiceBroker mediaServiceBroker;
    private final EncodeJobService encodeJobService;

    protected ProcessCaptureSessions(UserService userService,
                                     UserAuthenticationService userAuthenticationService,
                                     @Value("${cron-user-email}") String cronUserEmail,
                                     CaptureSessionService captureSessionService,
                                     MediaServiceBroker mediaServiceBroker,
                                     EncodeJobService encodeJobService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.captureSessionService = captureSessionService;
        this.mediaServiceBroker = mediaServiceBroker;
        this.encodeJobService = encodeJobService;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Starting process capture session task");

        var mediaService = mediaServiceBroker.getEnabledMediaService();
        encodeJobService.findAllProcessing().forEach(job -> checkJob(job, mediaService));

        encodeJobService.findAllProcessing()
            .stream()
            .filter(job -> job.getCreatedAt().before(Timestamp.from(Instant.now().minusSeconds(120))))
            .forEach(job -> {
                log.error(
                    "Processing job {} for capture session {} has timed out",
                    job.getJobName(),
                    job.getCaptureSessionId()
                );
                encodeJobService.delete(job.getId());
                captureSessionService.stopCaptureSession(job.getCaptureSessionId(), RecordingStatus.FAILURE, null);
            });

        log.info("Finished process capture session task");
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
                default ->
                    log.info(
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
            captureSessionService.stopCaptureSession(job.getCaptureSessionId(), RecordingStatus.FAILURE, null);
        }
    }

    private void onEncodeFromIngestProcessingComplete(EncodeJobDTO job, IMediaService mediaService) {
        log.info("EncodeFromIngest for capture session {} is complete", job.getCaptureSessionId());
        var jobName = mediaService.triggerProcessingStep2(job.getRecordingId());

        if (jobName == null) {
            log.info("No recording found for capture session {}", job.getCaptureSessionId());
            encodeJobService.delete(job.getId());
            captureSessionService.stopCaptureSession(job.getCaptureSessionId(), RecordingStatus.NO_RECORDING, null);
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
            captureSessionService.stopCaptureSession(
                job.getCaptureSessionId(),
                RecordingStatus.RECORDING_AVAILABLE,
                job.getRecordingId()
            );
        } else {
            log.error("Final asset not found for capture session {}", job.getCaptureSessionId());
            captureSessionService.stopCaptureSession(job.getCaptureSessionId(), RecordingStatus.FAILURE, null);
        }
    }

    private String getTransformName(EncodeTransform transform) {
        return switch (transform) {
            case ENCODE_FROM_INGEST -> MediaKind.ENCODE_FROM_INGEST_TRANSFORM;
            case ENCODE_FROM_MP4 -> MediaKind.ENCODE_FROM_MP4_TRANSFORM;
        };
    }
}
