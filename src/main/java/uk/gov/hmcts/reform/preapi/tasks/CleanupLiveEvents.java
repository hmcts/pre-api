package uk.gov.hmcts.reform.preapi.tasks;

import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.flow.StoppedLiveEventsNotificationDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.LiveEventCleanupException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.Batcher;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper.getSanitisedLiveEventId;

@Slf4j
@Component
public class CleanupLiveEvents extends RobotUserTask {
    private final MediaServiceBroker mediaServiceBroker;
    private final CaptureSessionService captureSessionService;
    private final BookingService bookingService;

    private final String platformEnv;

    private final int batchSize;
    private final int batchCooldownTime;
    private final int jobPollingInterval;

    private final Map<UUID, CleanupTask> liveEventCleanupMap = new ConcurrentHashMap<>();

    @Autowired
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public CleanupLiveEvents(MediaServiceBroker mediaServiceBroker,
                      CaptureSessionService captureSessionService,
                      BookingService bookingService,
                      UserService userService,
                      UserAuthenticationService userAuthenticationService,
                      @Value("${cron-user-email}") String cronUserEmail,
                      @Value("${platform-env}") String platformEnv,
                      @Value("${tasks.cleanup-live-events.batch-size}") int batchSize,
                      @Value("${tasks.cleanup-live-events.cooldown}") int batchCooldownTime,
                      @Value("${tasks.cleanup-live-events.job-poll-interval}") int jobPollingInterval) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.mediaServiceBroker = mediaServiceBroker;
        this.captureSessionService = captureSessionService;
        this.bookingService = bookingService;
        this.platformEnv = platformEnv;
        this.batchSize = batchSize;
        this.batchCooldownTime = batchCooldownTime;
        this.jobPollingInterval = jobPollingInterval;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Running CleanupLiveEvents task");

        IMediaService mediaService = mediaServiceBroker.getEnabledMediaService();

        // Find all Live events currently running
        List<LiveEventDTO> liveEvents = mediaService.getLiveEvents()
            .stream()
            .filter(liveEventDTO -> liveEventDTO.getResourceState().equals(LiveEventResourceState.RUNNING.toString()))
            .toList();

        // handle invalid named live events (no capture session)
        List<LiveEventDTO> invalidNamedEvents = liveEvents.stream()
            .filter(event -> {
                try {
                    captureSessionService.findByLiveEventId(event.getName());
                    return false;
                } catch (NotFoundException e) {
                    return true;
                }
            })
            .toList();
        cleanupLiveEventsInBatches(invalidNamedEvents);

        // Set up map
        liveEvents.stream()
            .filter(event -> {
                try {
                    captureSessionService.findByLiveEventId(event.getName());
                    return true;
                } catch (NotFoundException e) {
                    return false;
                }
            })
            .forEach(liveEventDTO -> {
                liveEventCleanupMap.put(
                    generateUuidFromLiveEventName(liveEventDTO.getName()),
                    new CleanupTask(CleanupTaskStatus.QUEUED, null, UUID.randomUUID())
                );
            });

        // Thread 1 - Cleanup Live Events in batches
        Thread threadCleanupLiveEvents = new Thread(() -> cleanupLiveEventsInBatches(liveEvents));

        // Thread 2 - Trigger and await processing of live event assets
        Thread threadProcessLiveEventAssets = new Thread(this::processLiveEvents);

        threadCleanupLiveEvents.start();
        threadProcessLiveEventAssets.start();
        try {
            threadCleanupLiveEvents.join();
            threadProcessLiveEventAssets.join();
        } catch (InterruptedException e) {
            log.error("Clean up live event thread interrupted", e);
            Thread.currentThread().interrupt();
            throw new LiveEventCleanupException("Thread interrupted during live event cleanup", e);
        }

        // Delete live events that remain
        mediaService.getLiveEvents().stream()
            .map(LiveEventDTO::getName)
            .forEach(liveEvent -> {
                log.info("Cleaning up remaining live event: {}", liveEvent);
                mediaService.stopLiveEvent(liveEvent);
            });

        // Handle past bookings that are unused
        handlePastBookings();

        log.info("Completed CleanupLiveEvents task");
    }

    private void handlePastBookings() {
        bookingService.findAllPastBookings()
            .forEach(booking -> {
                CreateCaptureSessionDTO captureSession = new CreateCaptureSessionDTO();
                captureSession.setId(UUID.randomUUID());
                captureSession.setBookingId(booking.getId());
                captureSession.setOrigin(RecordingOrigin.PRE);
                captureSession.setStatus(RecordingStatus.NO_RECORDING);
                log.info("Found old booking: {}. Creating NO_RECORDING capture session: {}",
                         booking.getId(), captureSession.getId());
                captureSessionService.upsert(captureSession);
            });

        UUID cronUserId = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getUserId();
        captureSessionService.findAllPastIncompleteCaptureSessions()
            .forEach(captureSession -> {
                CreateCaptureSessionDTO dto = new CreateCaptureSessionDTO();
                dto.setId(captureSession.getId());
                dto.setBookingId(captureSession.getBookingId());
                dto.setOrigin(captureSession.getOrigin());
                dto.setIngestAddress(captureSession.getIngestAddress());
                dto.setLiveOutputUrl(captureSession.getLiveOutputUrl());
                dto.setStartedAt(captureSession.getStartedAt());
                dto.setStartedByUserId(captureSession.getStartedByUserId());
                dto.setFinishedAt(Timestamp.from(Instant.now()));
                dto.setFinishedByUserId(cronUserId);
                dto.setStatus(RecordingStatus.NO_RECORDING);
                log.info("Found old capture session: {}. Updating to NO_RECORDING.", dto.getId());
                captureSessionService.upsert(dto);
            });
    }

    private void processLiveEvents() {
        signInRobotUser();
        IMediaService mediaService = mediaServiceBroker.getEnabledMediaService();

        do {
            try {
                Thread.sleep(jobPollingInterval);
            } catch (InterruptedException e) {
                // Do nothing
                Thread.currentThread().interrupt();
            }

            liveEventCleanupMap.entrySet()
                .stream()
                .filter(entry ->
                            entry.getValue().getStatus() != CleanupTaskStatus.QUEUED
                                && entry.getValue().getStatus() != CleanupTaskStatus.NO_RECORDING
                                && entry.getValue().getStatus() != CleanupTaskStatus.RECORDING_AVAILABLE)
                .forEach(entry -> {
                    switch (entry.getValue().getStatus()) {
                        case READY -> {
                            onCaptureSessionReady(entry, mediaService);
                        }
                        case PROCESSING_1 -> {
                            onCaptureSessionProcessingStep1(entry, mediaService);
                        }
                        case PROCESSING_2 -> {
                            onCaptureSessionProcessingStep2(entry, mediaService);
                        }
                        default -> {
                            // Do nothing
                        }
                    }
                    log.info(
                        "Live event cleanup task status after check: Capture Session: {}, Status: {}, Current Job: {}",
                        entry.getKey(),
                        entry.getValue().getStatus(),
                        entry.getValue().getCurrentJobName()
                    );
                });

        } while (!liveEventCleanupMap.entrySet()
            .stream()
            .allMatch(entry ->
                          entry.getValue().getStatus() == CleanupTaskStatus.NO_RECORDING
                              || entry.getValue().getStatus() == CleanupTaskStatus.RECORDING_AVAILABLE));
    }

    private void onCaptureSessionReady(Map.Entry<UUID, CleanupTask> task, IMediaService mediaService) {
        UUID captureSessionId = task.getKey();
        CleanupTask currentTask = task.getValue();

        CaptureSessionDTO captureSession = captureSessionService.findById(captureSessionId);
        String jobName = mediaService.triggerProcessingStep1(
            captureSession,
            getSanitisedLiveEventId(captureSessionId),
            currentTask.getRecordingId()
        );

        if (jobName == null) {
            currentTask.setStatus(CleanupTaskStatus.NO_RECORDING);
            captureSessionService.stopCaptureSession(
                captureSessionId,
                RecordingStatus.NO_RECORDING,
                currentTask.getRecordingId()
            );
            return;
        }

        currentTask.setCurrentJobName(jobName);
        currentTask.setStatus(CleanupTaskStatus.PROCESSING_1);
    }

    private void onCaptureSessionProcessingStep1(Map.Entry<UUID, CleanupTask> task, IMediaService mediaService) {
        UUID captureSessionId = task.getKey();
        CleanupTask currentTask = task.getValue();

        switch (mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_INGEST_TRANSFORM, currentTask.getCurrentJobName())) {
            case RECORDING_AVAILABLE -> {
                // trigger processing step 2
                String jobName = mediaService.triggerProcessingStep2(currentTask.getRecordingId());

                if (jobName == null) {
                    log.error("Failed to trigger processing step 2 for capture session {}", captureSessionId);
                    captureSessionService.stopCaptureSession(
                        captureSessionId,
                        RecordingStatus.FAILURE,
                        currentTask.getRecordingId()
                    );
                    currentTask.setStatus(CleanupTaskStatus.NO_RECORDING);
                    return;
                }
                currentTask.setCurrentJobName(jobName);
                currentTask.setStatus(CleanupTaskStatus.PROCESSING_2);
            }
            case FAILURE -> {
                log.error("Processing job {} failed for capture session {}",
                          currentTask.getCurrentJobName(),
                          captureSessionId);
                captureSessionService.stopCaptureSession(
                    captureSessionId,
                    RecordingStatus.FAILURE,
                    currentTask.getRecordingId()
                );
                currentTask.setStatus(CleanupTaskStatus.NO_RECORDING);
            }
            default -> {
                // still processing
            }
        }
    }

    private void onCaptureSessionProcessingStep2(Map.Entry<UUID, CleanupTask> task, IMediaService mediaService) {
        UUID captureSessionId = task.getKey();
        CleanupTask currentTask = task.getValue();

        switch (mediaService.hasJobCompleted(MediaKind.ENCODE_FROM_MP4_TRANSFORM, currentTask.getCurrentJobName())) {
            case RECORDING_AVAILABLE -> {
                // verify final asset exists
                if (mediaService.verifyFinalAssetExists(currentTask.getRecordingId()).equals(RecordingStatus.FAILURE)) {
                    log.error("Final asset not found for capture session {}", captureSessionId);
                    captureSessionService.stopCaptureSession(
                        captureSessionId,
                        RecordingStatus.FAILURE,
                        currentTask.getRecordingId()
                    );
                    currentTask.setStatus(CleanupTaskStatus.NO_RECORDING);
                    return;
                }
                log.info("Final asset found for capture session {}", captureSessionId);
                captureSessionService.stopCaptureSession(
                    captureSessionId,
                    RecordingStatus.RECORDING_AVAILABLE,
                    currentTask.getRecordingId()
                );
                currentTask.setStatus(CleanupTaskStatus.RECORDING_AVAILABLE);
            }
            case FAILURE -> {
                log.error("Processing job {} failed for capture session {}",
                          currentTask.getCurrentJobName(),
                          captureSessionId);
                captureSessionService.stopCaptureSession(
                    captureSessionId,
                    RecordingStatus.FAILURE,
                    currentTask.getRecordingId()
                );
                currentTask.setStatus(CleanupTaskStatus.NO_RECORDING);
            }
            default -> {
                // still processing
            }
        }
    }

    private void cleanupLiveEventsInBatches(List<LiveEventDTO> liveEvents) {
        signInRobotUser();
        Batcher.batchProcess(
            liveEvents,
            batchSize,
            liveEventDTO -> {
                String liveEventName = liveEventDTO.getName();
                log.info("Cleaning up live event {}", liveEventName);
                cleanupLiveEvent(mediaServiceBroker.getEnabledMediaService(), liveEventName);
            },
            batch -> {
                try {
                    Thread.sleep(batchCooldownTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        );
    }

    private void cleanupLiveEvent(IMediaService mediaService, String liveEventName) {
        try {
            CaptureSessionDTO captureSession = captureSessionService.findByLiveEventId(liveEventName);
            log.info("Capture session {} is processing", captureSession.getId());
            captureSession = captureSessionService.stopCaptureSession(
                captureSession.getId(),
                RecordingStatus.PROCESSING,
                null
            );
            mediaService.cleanupStoppedLiveEvent(liveEventName);
            CleanupTask currentTask = liveEventCleanupMap.get(captureSession.getId());
            currentTask.setStatus(CleanupTaskStatus.READY);
        } catch (NotFoundException e) {
            if (platformEnv.equals("Production")) {
                log.error("Error stopping live event {}", liveEventName, e);
                return;
            }
            log.info("Stopping live event without associated capture session: {}", liveEventName);
            try {
                mediaService.cleanupStoppedLiveEvent(liveEventName);
            } catch (Exception ex) {
                log.error("Error stopping live event {}", liveEventName, ex);
            }
        } catch (Exception e) {
            log.error("Error stopping live event {}", liveEventName, e);
        }
    }

    private UUID generateUuidFromLiveEventName(String liveEventId) {
        return new UUID(
            Long.parseUnsignedLong(liveEventId.substring(0, 16), 16),
            Long.parseUnsignedLong(liveEventId.substring(16), 16)
        );
    }

    @Data
    @AllArgsConstructor
    private static class CleanupTask {
        private CleanupTaskStatus status;
        private String currentJobName;
        private UUID recordingId;
    }

    private enum CleanupTaskStatus {
        QUEUED,
        READY,
        PROCESSING_1,
        PROCESSING_2,
        NO_RECORDING,
        RECORDING_AVAILABLE,
    }
}
