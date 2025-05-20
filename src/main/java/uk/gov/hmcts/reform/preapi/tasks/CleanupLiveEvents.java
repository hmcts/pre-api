package uk.gov.hmcts.reform.preapi.tasks;

import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.UUID;

@Slf4j
@Component
public class CleanupLiveEvents extends RobotUserTask {

    private final MediaServiceBroker mediaServiceBroker;
    private final CaptureSessionService captureSessionService;
    private final RecordingService recordingService;

    private final String platformEnv;

    @Autowired
    CleanupLiveEvents(MediaServiceBroker mediaServiceBroker,
                      CaptureSessionService captureSessionService,
                      RecordingService recordingService,
                      UserService userService,
                      UserAuthenticationService userAuthenticationService,
                      @Value("${cron-user-email}") String cronUserEmail,
                      @Value("${platform-env}") String platformEnv) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.mediaServiceBroker = mediaServiceBroker;
        this.captureSessionService = captureSessionService;
        this.recordingService = recordingService;
        this.platformEnv = platformEnv;
    }

    @Override
    public void run() throws RuntimeException {
        signInRobotUser();
        log.info("Running CleanupLiveEvents task");

        var mediaService = mediaServiceBroker.getEnabledMediaService();

        // Find all Live events currently running and stop and delete them along with their streaming endpoints and
        // locators
        var liveEvents = mediaService.getLiveEvents();
        liveEvents.stream()
                  .filter(liveEventDTO -> liveEventDTO
                      .getResourceState().equals(LiveEventResourceState.RUNNING.toString())
                  )
                  .forEach(liveEventDTO -> {
                      log.info("Finding capture session by live event id {}", liveEventDTO.getName());
                      try {
                          var captureSession = captureSessionService.findByLiveEventId(liveEventDTO.getName());
                          var search = new SearchRecordings();
                          log.info("Finding recordings by capture session {}", captureSession.getId());
                          search.setCaptureSessionId(captureSession.getId());
                          var recordings = recordingService.findAll(search, false, Pageable.unpaged());

                          if (recordings.isEmpty()) {
                              log.info("No recordings found for capture session {}", captureSession.getId());
                              stopLiveEvent(
                                  mediaService,
                                  captureSession,
                                  UUID.randomUUID(),
                                  liveEventDTO.getName()
                              );
                          } else {
                              recordings.forEach(recording -> {
                                  log.info(
                                      "{} recordings found for capture session {}",
                                      recordings.getSize(),
                                      captureSession.getId()
                                  );
                                  stopLiveEvent(
                                      mediaService,
                                      captureSession,
                                      recording.getId(),
                                      liveEventDTO.getName()
                                  );
                              });
                          }
                      } catch (NotFoundException e) {
                          if (platformEnv.equals("Production")) {
                              log.error("Error stopping live event {}", liveEventDTO.getName(), e);
                              return;
                          }
                          log.info("Stopping live event without associated capture session: {}",
                                   liveEventDTO.getName());
                          mediaService.cleanupStoppedLiveEvent(liveEventDTO.getName());
                      } catch (Exception e) {
                          log.error("Error stopping live event {}", liveEventDTO.getName(), e);
                      }
                  });

        log.info("Completed CleanupLiveEvents task");
    }

    private void stopLiveEvent(IMediaService mediaService,
                               CaptureSessionDTO captureSession,
                               UUID recordingId,
                               String liveEventId) {
        try {
            // This shouldn't happen but as this is the cleanup cron we do want to ensure the Live Events in AMS/MK
            // are terminated and cleaned up.
            // A manual process will be needed to investigate why the CaptureSession is in an unexpected state.
            if (captureSession.getStatus() != RecordingStatus.STANDBY
                && captureSession.getStatus() != RecordingStatus.RECORDING
                && captureSession.getStatus() != RecordingStatus.PROCESSING) {
                log.error(
                    "CaptureSession {} is in an unexpected state: {}",
                    captureSession.getId(),
                    captureSession.getStatus()
                );
                mediaService.cleanupStoppedLiveEvent(liveEventId);
                return;
            }
            var updatedCaptureSession = captureSessionService.stopCaptureSession(
                captureSession.getId(),
                RecordingStatus.PROCESSING,
                recordingId
            );
            log.info("Stopping live event {}", liveEventId);
            var status = mediaService.stopLiveEvent(updatedCaptureSession, recordingId);
            captureSessionService.stopCaptureSession(updatedCaptureSession.getId(), status, recordingId);
            log.info("Stopped live event {} with status {}", liveEventId, status);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to stop live event for capture session {}", captureSession.getId(), e);
            captureSessionService.stopCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, recordingId);
        } catch (Exception e) {
            log.error("Failed to stop live event for capture session {}", captureSession.getId(), e);
            captureSessionService.stopCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, recordingId);
        }
    }
}
