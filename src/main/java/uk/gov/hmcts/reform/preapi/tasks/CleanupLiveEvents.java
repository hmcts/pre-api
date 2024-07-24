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
import uk.gov.hmcts.reform.preapi.dto.StoppedLiveEventsNotificationDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.email.FlowHttpClient;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private final FlowHttpClient flowHttpClient;

    @Autowired
    CleanupLiveEvents(MediaServiceBroker mediaServiceBroker,
                      CaptureSessionService captureSessionService,
                      BookingService bookingService,
                      RecordingService recordingService,
                      UserService userService,
                      UserAuthenticationService userAuthenticationService,
                      @Value("${cron-user-email}") String cronUserEmail,
                      FlowHttpClient flowHttpClient) {
        this.mediaServiceBroker = mediaServiceBroker;
        this.captureSessionService = captureSessionService;
        this.bookingService = bookingService;
        this.recordingService = recordingService;
        this.userService = userService;
        this.userAuthenticationService = userAuthenticationService;
        this.cronUserEmail = cronUserEmail;
        this.flowHttpClient = flowHttpClient;
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
        try {
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
                          } catch (Exception e) {
                              log.error("Error stopping live event {}", liveEventDTO.getName(), e);
                          }
                      });
        } catch (Exception ex) {
            log.error("Unexpected error occurred when trying to stop live events", ex);
        }

        notifyUsers(liveEvents);

        log.info("Completed CleanupLiveEvents task");
    }

    private void notifyUsers(List<LiveEventDTO> liveEvents) {
        var stoppedLiveEventsNotificationDTOs = liveEvents
                .stream()
                .filter(liveEventDTO -> liveEventDTO
                                .getResourceState().equals(LiveEventResourceState.RUNNING.toString())
                )
                .map(liveEventDTO -> {
                    var captureSession = captureSessionService.findByLiveEventId(liveEventDTO.getName());
                    if (captureSession != null && captureSession.getStatus()
                                                                .equals(RecordingStatus.RECORDING_AVAILABLE)) {
                        var booking = bookingService.findById(captureSession.getBookingId());
                        if (booking != null) {
                            return booking.getShares().stream()
                                          .map(shareBooking -> userService.findById(
                                                  shareBooking.getSharedWithUser().getId())
                                          )
                                          .filter(user -> user != null && user.getDeletedAt() == null)
                                          .map(user -> StoppedLiveEventsNotificationDTO
                                                  .builder()
                                                  .email(user.getEmail())
                                                  .firstName(user.getFirstName())
                                                  .caseReference(booking.getCaseDTO().getReference())
                                                  .courtName(booking.getCaseDTO().getCourt().getName())
                                                  .build())
                                          .collect(Collectors.toList());
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        flowHttpClient.emailAfterStoppingLiveEvents(stoppedLiveEventsNotificationDTOs);
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
                log.info(
                        "CaptureSession {} is in an unexpected state: {}",
                        captureSession.getId(),
                        captureSession.getStatus()
                );
                mediaService.cleanupStoppedLiveEvent(liveEventId);
            }
            var updatedCaptureSession = captureSessionService.stopCaptureSession(
                    captureSession.getId(),
                    RecordingStatus.PROCESSING,
                    recordingId
            );
            log.info("Stopping live event {}", liveEventId);
            var status = mediaService.stopLiveEvent(updatedCaptureSession, recordingId);
            captureSessionService.stopCaptureSession(updatedCaptureSession.getId(), status, recordingId);
            log.info("Stopped live event {}", liveEventId);
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
