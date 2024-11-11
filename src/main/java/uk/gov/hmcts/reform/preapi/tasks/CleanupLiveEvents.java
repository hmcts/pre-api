package uk.gov.hmcts.reform.preapi.tasks;

import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.flow.StoppedLiveEventsNotificationDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceBroker;
import uk.gov.hmcts.reform.preapi.email.StopLiveEventNotifierFlowClient;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.UUID;

@Component
@Slf4j
public class CleanupLiveEvents implements Runnable {

    private final MediaServiceBroker mediaServiceBroker;

    private final CaptureSessionService captureSessionService;

    private final BookingService bookingService;

    private final RecordingService recordingService;

    private final UserService userService;

    private final UserAuthenticationService userAuthenticationService;

    private final String cronUserEmail;

    private final String platformEnv;

    private final StopLiveEventNotifierFlowClient stopLiveEventNotifierFlowClient;

    private final EmailServiceBroker emailServiceBroker;

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
                      EmailServiceBroker emailServiceBroker) {
        this.mediaServiceBroker = mediaServiceBroker;
        this.captureSessionService = captureSessionService;
        this.bookingService = bookingService;
        this.recordingService = recordingService;
        this.userService = userService;
        this.userAuthenticationService = userAuthenticationService;
        this.cronUserEmail = cronUserEmail;
        this.platformEnv = platformEnv;
        this.stopLiveEventNotifierFlowClient = stopLiveEventNotifierFlowClient;
        this.emailServiceBroker = emailServiceBroker;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Sign in as robot user");
        var user = userService.findByEmail(cronUserEmail);

        var appAccess = user.getAppAccess().stream().findFirst()
                            .orElseThrow(() -> new RuntimeException(
                                "Failed to authenticate as cron user with email " + cronUserEmail)
                            );
        var userAuth = userAuthenticationService.validateUser(appAccess.getId().toString())
                                                .orElseThrow(() -> new RuntimeException(
                                                    "Failed to authenticate as cron user with email "
                                                        + cronUserEmail)
                                                );
        SecurityContextHolder.getContext().setAuthentication(userAuth);

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
                      var sendNotification = false;
                      try {
                          var captureSession = captureSessionService.findByLiveEventId(liveEventDTO.getName());
                          var search = new SearchRecordings();
                          log.info("Finding recordings by capture session {}", captureSession.getId());
                          search.setCaptureSessionId(captureSession.getId());
                          var recordings = recordingService.findAll(search, false, Pageable.unpaged());

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
                                  var booking = bookingService.findById(captureSession.getBookingId());

                                  var shares = booking.getShares();
                                  var toNotify = shares.stream()
                                                        .map(shareBooking -> userService.findById(
                                                            shareBooking.getSharedWithUser().getId())
                                                        )
                                                        .map(u -> StoppedLiveEventsNotificationDTO
                                                            .builder()
                                                            .email(u.getEmail())
                                                            .firstName(u.getFirstName())
                                                            .caseReference(booking.getCaseDTO().getReference())
                                                            .courtName(booking.getCaseDTO().getCourt().getName())
                                                            .build())
                                                        .toList();
                                  if (!toNotify.isEmpty()) {
                                      log.info("Sending email notifications to {} user(s)", toNotify.size());
                                      if (!emailServiceBroker.isEnabled()) {
                                          stopLiveEventNotifierFlowClient.emailAfterStoppingLiveEvents(toNotify);
                                      } else {
                                          var emailService = emailServiceBroker.getEnabledEmailService();
                                          var forCase = new Case();
                                          forCase.setReference(booking.getCaseDTO().getReference());
                                          var court = new Court();
                                          court.setName(booking.getCaseDTO().getCourt().getName());
                                          forCase.setCourt(court);
                                          shares.forEach(share -> {
                                              var emailUser = new User();
                                              emailUser.setEmail(share.getSharedWithUser().getEmail());
                                              emailUser.setFirstName(share.getSharedWithUser().getFirstName());
                                              emailUser.setLastName(share.getSharedWithUser().getLastName());
                                              emailService.recordingReady(emailUser, forCase);
                                          });
                                      }
                                  } else {
                                      log.info("No users to notify for capture session {}", captureSession.getId());
                                  }
                              } catch (NotFoundException e) {
                                  log.error(e.getMessage());
                              }
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
            var updatedCaptureSession = captureSessionService.stopCaptureSession(
                captureSession.getId(),
                RecordingStatus.PROCESSING,
                recordingId
            );
            log.info("Stopping live event {}", liveEventId);
            var status = mediaService.stopLiveEvent(updatedCaptureSession, recordingId);
            var stoppedCaptureSession = captureSessionService.stopCaptureSession(
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

    private UUID generateUuidFromLiveEventName(String liveEventId) {
        return new UUID(
            Long.parseUnsignedLong(liveEventId.substring(0, 16), 16),
            Long.parseUnsignedLong(liveEventId.substring(16), 16)
        );
    }
}
