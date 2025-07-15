package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.Batcher;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

@Slf4j
@Component
public class StartLiveEvents extends RobotUserTask {

    private final MediaServiceBroker mediaServiceBroker;
    private final BookingService bookingService;
    private final CaptureSessionService captureSessionService;

    private final int batchSize;
    private final int pollInterval;

    @Autowired
    public StartLiveEvents(UserService userService,
                           UserAuthenticationService userAuthenticationService,
                           CaptureSessionService captureSessionService, MediaServiceBroker mediaServiceBroker,
                           BookingService bookingService,
                           @Value("${cron-user-email}") String cronUserEmail,
                           @Value("${tasks.start-live-event.batch-size}") int batchSize,
                           @Value("${tasks.start-live-event.poll-interval}") int pollInterval) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.mediaServiceBroker = mediaServiceBroker;
        this.bookingService = bookingService;
        this.captureSessionService = captureSessionService;
        this.batchSize = batchSize;
        this.pollInterval = pollInterval;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Running StartLiveEvents task");

        // find all bookings scheduled for today and create a capture session + start a live event
        // only if capture session doesn't already exist
        Batcher.batchProcess(
            bookingService.findAllBookingsForToday()
                .stream()
                .filter(b -> b.getCaptureSessions().isEmpty())
                .map(BookingDTO::getId)
                .map(this::initCaptureSessionForBooking)
                .filter(Objects::nonNull)
                .toList(),
            batchSize,
            this::startLiveEvent,
            this::awaitIngestAddresses
        );
    }

    private UUID initCaptureSessionForBooking(UUID bookingId) {
        log.info("Creating capture session for booking {}", bookingId);

        var dto = new CreateCaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(bookingId);
        dto.setOrigin(RecordingOrigin.PRE);
        dto.setStatus(RecordingStatus.INITIALISING);

        try {
            captureSessionService.upsert(dto);
        } catch (Exception e) {
            log.error("Failed to create capture session for booking {}", bookingId, e);
            return null;
        }

        return dto.getId();
    }

    private void startLiveEvent(UUID captureSessionId) {
        log.info("Starting live event for capture session {}", captureSessionId);

        var mediaService = mediaServiceBroker.getEnabledMediaService();
        var dto = captureSessionService.findById(captureSessionId);

        try {
            mediaService.startLiveEvent(dto);
        } catch (Exception e) {
            log.error("Failed to start live event for capture session {}", captureSessionId, e);
            captureSessionService.startCaptureSession(captureSessionId, RecordingStatus.FAILURE, null);
            return;
        }

        captureSessionService.startCaptureSession(captureSessionId, RecordingStatus.INITIALISING, null);
    }

    private void awaitIngestAddresses(List<UUID> captureSessionIds) {
        log.info("Awaiting ingest addresses for capture sessions");
        var mediaService = mediaServiceBroker.getEnabledMediaService();

        var startingCaptureSessions = captureSessionIds
            .stream()
            .filter(id -> captureSessionService.findById(id).getStatus().equals(RecordingStatus.INITIALISING))
            .toList();

        try {
            do {
                Thread.sleep(pollInterval);
                var liveEvents = mediaService.getLiveEvents();
                startingCaptureSessions = startingCaptureSessions.stream()
                    .filter(id -> {
                        var result = tryGetIngestAddress(id, liveEvents);
                        if (result) {
                            log.info("Ingest address obtained for capture session {}", id);
                        }
                        return !result;
                    })
                    .toList();
            } while (!startingCaptureSessions.isEmpty());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            startingCaptureSessions.forEach(id -> {
                log.error("Failed to await ingest address for capture session {}", id, e);
                captureSessionService.startCaptureSession(id, RecordingStatus.FAILURE, null);
            });
        }
    }

    private boolean tryGetIngestAddress(UUID captureSessionId, List<LiveEventDTO> liveEvents) {
        return liveEvents.stream()
            .filter(e -> e.getName().equals(MediaResourcesHelper.getSanitisedLiveEventId(captureSessionId)))
            .findFirst()
            .map(this::getIngestAddress)
            .map(inputRtmp -> {
                captureSessionService.startCaptureSession(captureSessionId, RecordingStatus.STANDBY, inputRtmp);
                return true;
            })
            .orElse(false);
    }

    private @Nullable String getIngestAddress(LiveEventDTO liveEvent) {
        return liveEvent != null && liveEvent.getResourceState().equals("Running") && liveEvent.getInputRtmp() != null
            ? liveEvent.getInputRtmp()
            : null;
    }
}
