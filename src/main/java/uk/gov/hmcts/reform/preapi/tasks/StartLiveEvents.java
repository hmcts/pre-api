package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.media.MediaResourcesHelper;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

@Slf4j
@Component
public class StartLiveEvents extends RobotUserTask {

    private final MediaServiceBroker mediaServiceBroker;
    private final BookingService bookingService;
    private final CaptureSessionService captureSessionService;

    @Autowired
    public StartLiveEvents(UserService userService,
                           UserAuthenticationService userAuthenticationService,
                           @Value("${cron-user-email}") String cronUserEmail,
                           MediaServiceBroker mediaServiceBroker,
                           BookingService bookingService,
                           CaptureSessionService captureSessionService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.mediaServiceBroker = mediaServiceBroker;
        this.bookingService = bookingService;
        this.captureSessionService = captureSessionService;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Running StartLiveEvents task");

        // find all bookings scheduled for today and create a capture session + start a live event
        // only if capture session doesn't already exist
        bookingService.searchBy(null,
                                null,
                                null,
                                Optional.of(Timestamp.valueOf(LocalDate.now().atStartOfDay())),
                                null,
                                null,
                                null,
                                null,
                                Pageable.unpaged())
            .stream()
            .filter(b -> b.getCaptureSessions().isEmpty())
            .map(BookingDTO::getId)
            .map(this::initCaptureSessionForBooking)
            .filter(Objects::nonNull)
            .filter(this::startLiveEvent)
            .forEach(this::awaitIngestAddress);
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

    private boolean startLiveEvent(UUID captureSessionId) {
        log.info("Starting live event for capture session {}", captureSessionId);

        var mediaService = mediaServiceBroker.getEnabledMediaService();
        var dto = captureSessionService.findById(captureSessionId);

        try {
            mediaService.startLiveEvent(dto);
        } catch (ConflictException e) {
            log.error("Live event already started for capture session: {}", captureSessionId, e);
            handleCaptureSessionFailure(captureSessionId, true);
            return false;
        } catch (Exception e) {
            log.error("Failed to start live event for capture session {}", captureSessionId, e);
            handleCaptureSessionFailure(captureSessionId, false);
            return false;
        }

        captureSessionService.startCaptureSession(captureSessionId, RecordingStatus.INITIALISING, null);
        return true;
    }

    private void awaitIngestAddress(UUID captureSessionId) {
        log.info("Awaiting ingest address for capture session {}", captureSessionId);
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        var liveEventName = MediaResourcesHelper.getSanitisedLiveEventId(captureSessionId);

        try {
            String inputRtmp;
            do {
                Thread.sleep(2000);
                inputRtmp = getIngestAddress(mediaService.getLiveEvent(liveEventName));
            } while (inputRtmp == null);

            log.info("Ingest address found for capture session {}", captureSessionId);
            captureSessionService.startCaptureSession(captureSessionId, RecordingStatus.STANDBY, inputRtmp);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Failed to await ingest address for capture session {}", captureSessionId, e);
            handleCaptureSessionFailure(captureSessionId, false);
        }
    }

    private @Nullable String getIngestAddress(LiveEventDTO liveEvent) {
        return liveEvent != null && liveEvent.getResourceState().equals("Running") && liveEvent.getInputRtmp() != null
            ? liveEvent.getInputRtmp()
            : null;
    }

    private void handleCaptureSessionFailure(UUID captureSessionId, boolean deleteOnFailure) {
        captureSessionService.startCaptureSession(captureSessionId, RecordingStatus.FAILURE, null);
        if (deleteOnFailure) {
            captureSessionService.deleteById(captureSessionId);
        }
    }
}
