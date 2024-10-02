package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class CaptureSessionService {

    private final RecordingService recordingService;
    private final CaptureSessionRepository captureSessionRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingService bookingService;
    private final String mediaService;

    @Autowired
    public CaptureSessionService(RecordingService recordingService,
                                 CaptureSessionRepository captureSessionRepository,
                                 BookingRepository bookingRepository,
                                 UserRepository userRepository,
                                 @Lazy BookingService bookingService,
                                 @Value("${media-service}") String mediaService) {
        this.recordingService = recordingService;
        this.captureSessionRepository = captureSessionRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.bookingService = bookingService;
        this.mediaService = mediaService;
    }

    @Transactional
    public CaptureSessionDTO findByLiveEventId(String liveEventId) {
        var liveEventUUID = new UUID(
            Long.parseUnsignedLong(liveEventId.substring(0, 16), 16),
            Long.parseUnsignedLong(liveEventId.substring(16), 16)
        );
        return this.findById(liveEventUUID);
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #id)")
    public CaptureSessionDTO findById(UUID id) {
        return captureSessionRepository
            .findByIdAndDeletedAtIsNull(id)
            .map(CaptureSessionDTO::new)
            .orElseThrow(() -> new NotFoundException("CaptureSession: " + id));
    }

    @Transactional
    public Page<CaptureSessionDTO> searchBy(
        String caseReference,
        UUID bookingId,
        RecordingOrigin origin,
        RecordingStatus recordingStatus,
        Optional<Timestamp> scheduledFor,
        UUID courtId,
        Pageable pageable
    ) {
        var until = scheduledFor.isEmpty()
            ? null
            : scheduledFor.map(t -> Timestamp.from(t.toInstant().plus(86399, ChronoUnit.SECONDS))).orElse(null);

        var auth = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication());
        var authorisedBookings = auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings();
        var authorisedCourt = auth.isPortalUser() || auth.isAdmin() ? null : auth.getCourtId();

        return captureSessionRepository
            .searchCaptureSessionsBy(
                caseReference,
                bookingId,
                origin,
                recordingStatus,
                courtId,
                scheduledFor.orElse(null),
                until,
                authorisedBookings,
                authorisedCourt,
                pageable
            )
            .map(CaptureSessionDTO::new);
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #id)")
    public void deleteById(UUID id) {
        var captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("CaptureSession: " + id));
        recordingService.deleteCascade(captureSession);
        captureSession.setDeleteOperation(true);
        captureSession.setDeletedAt(Timestamp.from(Instant.now()));
        captureSessionRepository.saveAndFlush(captureSession);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteCascade(Booking booking) {
        captureSessionRepository
            .findAllByBookingAndDeletedAtIsNull(booking)
            .forEach(captureSession -> {
                recordingService.deleteCascade(captureSession);
                captureSession.setDeleteOperation(true);
                captureSession.setDeletedAt(Timestamp.from(Instant.now()));
                captureSessionRepository.save(captureSession);
            });
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #createCaptureSessionDTO)")
    public UpsertResult upsert(CreateCaptureSessionDTO createCaptureSessionDTO) {
        var foundCaptureSession = captureSessionRepository.findById(createCaptureSessionDTO.getId());

        if (foundCaptureSession.isPresent() && foundCaptureSession.get().isDeleted()) {
            throw new ResourceInDeletedStateException("CaptureSessionDTO", createCaptureSessionDTO.getId().toString());
        }

        var captureSession = foundCaptureSession.orElse(new CaptureSession());

        var booking = bookingRepository
            .findByIdAndDeletedAtIsNull(createCaptureSessionDTO.getBookingId())
            .orElseThrow(() -> new NotFoundException("Booking: " + createCaptureSessionDTO.getBookingId()));

        if (booking.getCaseId().getState() != CaseState.OPEN) {
            throw new ResourceInWrongStateException(
                "CaptureSession",
                createCaptureSessionDTO.getId(),
                booking.getCaseId().getState(),
                "OPEN"
            );
        }

        var startedByUser = createCaptureSessionDTO.getStartedByUserId() != null
            ? userRepository
            .findByIdAndDeletedAtIsNull(createCaptureSessionDTO.getStartedByUserId())
            .orElseThrow(() -> new NotFoundException("User: " + createCaptureSessionDTO.getStartedByUserId()))
            : null;

        var finishedByUser = createCaptureSessionDTO.getFinishedByUserId() != null
            ? userRepository
            .findByIdAndDeletedAtIsNull(createCaptureSessionDTO.getFinishedByUserId())
            .orElseThrow(() -> new NotFoundException("User: " + createCaptureSessionDTO.getFinishedByUserId()))
            : null;

        captureSession.setId(createCaptureSessionDTO.getId());
        captureSession.setBooking(booking);
        captureSession.setOrigin(createCaptureSessionDTO.getOrigin());
        captureSession.setIngestAddress(createCaptureSessionDTO.getIngestAddress());
        captureSession.setLiveOutputUrl(createCaptureSessionDTO.getLiveOutputUrl());
        captureSession.setStartedAt(createCaptureSessionDTO.getStartedAt());
        captureSession.setStartedByUser(startedByUser);
        captureSession.setFinishedAt(createCaptureSessionDTO.getFinishedAt());
        captureSession.setFinishedByUser(finishedByUser);
        captureSession.setStatus(createCaptureSessionDTO.getStatus());

        captureSessionRepository.save(captureSession);

        var isUpdate = foundCaptureSession.isPresent();

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #id)")
    public void undelete(UUID id) {
        var entity =
            captureSessionRepository.findById(id).orElseThrow(() -> new NotFoundException("Capture Session: " + id));
        bookingService.undelete(entity.getBooking().getId());
        if (!entity.isDeleted()) {
            return;
        }
        entity.setDeletedAt(null);
        captureSessionRepository.save(entity);
    }

    @Transactional
    public CaptureSessionDTO startCaptureSession(UUID id, RecordingStatus status, String ingestAddress) {
        var captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("Capture Session: " + id));

        var userId = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getUserId();
        var user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User: " + userId));

        captureSession.setStartedByUser(user);
        captureSession.setStartedAt(Timestamp.from(Instant.now()));

        captureSession.setStatus(status);
        captureSession.setIngestAddress(ingestAddress);

        captureSessionRepository.save(captureSession);
        return new CaptureSessionDTO(captureSession);
    }

    @Transactional
    public CaptureSessionDTO stopCaptureSession(UUID captureSessionId,
                                                RecordingStatus status,
                                                UUID recordingId) {
        var captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(captureSessionId)
            .orElseThrow(() -> new NotFoundException("Capture Session: " + captureSessionId));

        log.info("Stopping capture session {} with status {}", captureSessionId, status);
        captureSession.setStatus(status);

        switch (status) {
            case PROCESSING -> {
                var userId = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getUserId();
                var user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User: " + userId));

                captureSession.setFinishedByUser(user);
                captureSession.setFinishedAt(Timestamp.from(Instant.now()));
            }
            case RECORDING_AVAILABLE -> {
                var recording = new CreateRecordingDTO();
                recording.setId(recordingId);
                recording.setCaptureSessionId(captureSessionId);
                recording.setVersion(1);
                recording.setFilename(Objects.equals(mediaService, MediaServiceBroker.MEDIA_SERVICE_AMS)
                                          ? "video_2000000_1280x720_4500.mp4"
                                          : "index_1280x720_4500k.mp4");
                recordingService.upsert(recording);
            }
            default -> {
            }
        }
        captureSessionRepository.saveAndFlush(captureSession);
        return new CaptureSessionDTO(captureSession);
    }

    @Transactional
    public CaptureSessionDTO setCaptureSessionStatus(UUID captureSessionId, RecordingStatus status) {
        var captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(captureSessionId)
            .orElseThrow(() -> new NotFoundException("Capture Session: " + captureSessionId));
        captureSession.setStatus(status);
        captureSessionRepository.save(captureSession);
        return new CaptureSessionDTO(captureSession);
    }
}
