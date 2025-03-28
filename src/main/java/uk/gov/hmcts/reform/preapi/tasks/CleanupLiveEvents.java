package uk.gov.hmcts.reform.preapi.tasks;

import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.flow.StoppedLiveEventsNotificationDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.StopLiveEventNotifierFlowClient;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class CleanupLiveEvents extends RobotUserTask {

    private final MediaServiceBroker mediaServiceBroker;
    private final CaptureSessionService captureSessionService;
    private final BookingService bookingService;
    private final RecordingService recordingService;
    private final StopLiveEventNotifierFlowClient stopLiveEventNotifierFlowClient;

    private final String platformEnv;

    private final EmailServiceFactory emailServiceFactory;

    private static final String ENV_PRODUCTION = "Production";

    @Autowired
    CleanupLiveEvents(MediaServiceBroker mediaServiceBroker,
                      CaptureSessionService captureSessionService,
                      BookingService bookingService,
                      RecordingService recordingService,
                      UserService userService,
                      UserAuthenticationService userAuthenticationService,
                      @Value("${cron-user-email}") String cronUserEmail,
                      @Value("${platform-env}") String platformEnv,
                      StopLiveEventNotifierFlowClient stopLiveEventNotifierFlowClient,
                      EmailServiceFactory emailServiceFactory) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.mediaServiceBroker = mediaServiceBroker;
        this.captureSessionService = captureSessionService;
        this.bookingService = bookingService;
        this.recordingService = recordingService;
        this.platformEnv = platformEnv;
        this.stopLiveEventNotifierFlowClient = stopLiveEventNotifierFlowClient;
        this.emailServiceFactory = emailServiceFactory;
    }

    @Override
    @SuppressWarnings("PMD.CognitiveComplexity")
    public void run() {
        signInRobotUser();
        log.info("Running CleanupLiveEvents task");

        IMediaService mediaService = mediaServiceBroker.getEnabledMediaService();

        // Find all Live events currently running and stop and delete them along with their streaming endpoints and
        // locators
        List<LiveEventDTO> liveEvents = mediaService.getLiveEvents();
        liveEvents.stream()
                  .filter(liveEventDTO -> liveEventDTO
                      .getResourceState().equals(LiveEventResourceState.RUNNING.toString())
                  )
                  .forEach(liveEventDTO -> {
                      log.info("Finding capture session by live event id {}", liveEventDTO.getName());
                      boolean sendNotification;
                      try {
                          CaptureSessionDTO captureSession = captureSessionService
                              .findByLiveEventId(liveEventDTO.getName());
                          SearchRecordings search = new SearchRecordings();
                          log.info("Finding recordings by capture session {}", captureSession.getId());
                          search.setCaptureSessionId(captureSession.getId());
                          Page<RecordingDTO> recordings = recordingService.findAll(search, false, Pageable.unpaged());

                          if (recordings.isEmpty()) {
                              log.info("No recordings found for capture session {}", captureSession.getId());
                              sendNotification = stopLiveEvent(
                                  mediaService,
                                  captureSession,
                                  UUID.randomUUID(),
                                  liveEventDTO.getName()
                              );
                          } else {
                              sendNotification = !recordings.map(recording -> {
                                  log.info(
                                      "{} recordings found for capture session {}",
                                      recordings.getSize(),
                                      captureSession.getId()
                                  );
                                  return stopLiveEvent(
                                      mediaService,
                                      captureSession,
                                      recording.getId(),
                                      liveEventDTO.getName()
                                  );
                              })
                                  .filter(b -> b)
                                  .toList()
                                  .isEmpty();
                          }

                          if (sendNotification) {
                              try {
                                  BookingDTO booking = bookingService.findById(captureSession.getBookingId());

                                  List<ShareBookingDTO> shares = booking.getShares();
                                  // @todo simplify this after 4.3 goes live S28-3692
                                  List<StoppedLiveEventsNotificationDTO> toNotify = shares.stream()
                                                        .map(shareBooking -> userService.findById(
                                                            shareBooking.getSharedWithUser().getId())
                                                        )
                                                        .map(u -> StoppedLiveEventsNotificationDTO
                                                            .builder()
                                                            .email(u.getEmail())
                                                            .firstName(u.getFirstName())
                                                            .lastName(u.getLastName())
                                                            .caseReference(booking.getCaseDTO().getReference())
                                                            .courtName(booking.getCaseDTO().getCourt().getName())
                                                            .build())
                                                        .toList();
                                  if (!toNotify.isEmpty()) {
                                      if (!emailServiceFactory.isEnabled()) {
                                          log.info("Sending email notifications to {} user(s)", toNotify.size());
                                          stopLiveEventNotifierFlowClient.emailAfterStoppingLiveEvents(toNotify);
                                      }
                                      // if GovNotify is enabled, users are notified via the
                                      // RecordingListener.onRecordingCreated method
                                  } else {
                                      log.info("No users to notify for capture session {}", captureSession.getId());
                                  }
                              } catch (NotFoundException e) {
                                  log.error(e.getMessage());
                              }
                          }
                      } catch (NotFoundException e) {
                          if (platformEnv.equals(ENV_PRODUCTION)) {
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

    private boolean stopLiveEvent(IMediaService mediaService,
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
                return false;
            }
            CaptureSessionDTO updatedCaptureSession = captureSessionService.stopCaptureSession(
                captureSession.getId(),
                RecordingStatus.PROCESSING,
                recordingId
            );
            log.info("Stopping live event {}", liveEventId);
            RecordingStatus status = mediaService.stopLiveEvent(updatedCaptureSession, recordingId);
            CaptureSessionDTO stoppedCaptureSession = captureSessionService.stopCaptureSession(
                updatedCaptureSession.getId(),
                status,
                recordingId
            );
            log.info("Stopped live event {}", liveEventId);
            return stoppedCaptureSession.getStatus().equals(RecordingStatus.RECORDING_AVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to stop live event for capture session {}", captureSession.getId(), e);
            captureSessionService.stopCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, recordingId);
            return false;
        } catch (Exception e) {
            log.error("Failed to stop live event for capture session {}", captureSession.getId(), e);
            captureSessionService.stopCaptureSession(captureSession.getId(), RecordingStatus.FAILURE, recordingId);
            return false;
        }
    }
}
