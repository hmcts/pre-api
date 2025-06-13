package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class CaptureSessionService {

    private final RecordingService recordingService;
    private final CaptureSessionRepository captureSessionRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingService bookingService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final AuditService auditService;

    @Autowired
    public CaptureSessionService(RecordingService recordingService,
                                 CaptureSessionRepository captureSessionRepository,
                                 BookingRepository bookingRepository,
                                 UserRepository userRepository,
                                 @Lazy BookingService bookingService,
                                 AzureFinalStorageService azureFinalStorageService,
                                 AuditService auditService) {
        this.recordingService = recordingService;
        this.captureSessionRepository = captureSessionRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.bookingService = bookingService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.auditService = auditService;
    }

    @Transactional
    public CaptureSessionDTO findByLiveEventId(String liveEventId) {
        try {
            UUID liveEventUUID = new UUID(
                Long.parseUnsignedLong(liveEventId.substring(0, 16), 16),
                Long.parseUnsignedLong(liveEventId.substring(16), 16)
            );
            return this.findById(liveEventUUID);
        } catch (Exception e) {
            throw (NotFoundException) new NotFoundException("CaptureSession: " + liveEventId).initCause(e);
        }
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
        Timestamp until = scheduledFor.isEmpty()
            ? null
            : scheduledFor.map(t -> Timestamp.from(t.toInstant().plus(86399, ChronoUnit.SECONDS))).orElse(null);

        UserAuthentication auth = (UserAuthentication) SecurityContextHolder.getContext().getAuthentication();
        List<UUID> authorisedBookings = auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings();
        UUID authorisedCourt = auth.isPortalUser() || auth.isAdmin() ? null : auth.getCourtId();

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
    public List<CaptureSession> findSessionsByDate(LocalDate date) {
        Timestamp fromTime = Timestamp.valueOf(date.atStartOfDay());
        Timestamp toTime = Timestamp.valueOf(date.atStartOfDay().plusDays(1));

        return captureSessionRepository
            .findAllByStartedAtIsBetweenAndDeletedAtIsNull(fromTime, toTime);
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #id)")
    public void deleteById(UUID id) {
        CaptureSession captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("CaptureSession: " + id));

        if (captureSession.getStatus() != RecordingStatus.RECORDING_AVAILABLE
            && captureSession.getStatus() != RecordingStatus.NO_RECORDING
            && captureSession.getStatus() != RecordingStatus.FAILURE) {
            throw new ResourceInWrongStateException(
                "Capture Session ("
                    + id
                    + ") must be in state RECORDING_AVAILABLE or NO_RECORDING to be deleted. Current state is "
                    + captureSession.getStatus()
            );
        }

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
                if (captureSession.getStatus() != RecordingStatus.RECORDING_AVAILABLE
                    && captureSession.getStatus() != RecordingStatus.NO_RECORDING
                    && captureSession.getStatus() != RecordingStatus.FAILURE) {
                    throw new ResourceInWrongStateException(
                        "Capture Session ("
                            + captureSession.getId()
                            + ") must be in state RECORDING_AVAILABLE or NO_RECORDING to be deleted. Current state is "
                            + captureSession.getStatus()
                    );
                }
                recordingService.deleteCascade(captureSession);
                captureSession.setDeleteOperation(true);
                captureSession.setDeletedAt(Timestamp.from(Instant.now()));
                captureSessionRepository.save(captureSession);
            });
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #createCaptureSessionDTO)")
    public UpsertResult upsert(CreateCaptureSessionDTO createCaptureSessionDTO) {
        Optional<CaptureSession> foundCaptureSession = captureSessionRepository
            .findById(createCaptureSessionDTO.getId());

        if (foundCaptureSession.isPresent() && foundCaptureSession.get().isDeleted()) {
            throw new ResourceInDeletedStateException("CaptureSessionDTO", createCaptureSessionDTO.getId().toString());
        }

        CaptureSession captureSession = foundCaptureSession.orElse(new CaptureSession());

        Booking booking = bookingRepository
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

        User startedByUser = createCaptureSessionDTO.getStartedByUserId() != null
            ? userRepository
            .findByIdAndDeletedAtIsNull(createCaptureSessionDTO.getStartedByUserId())
            .orElseThrow(() -> new NotFoundException("User: " + createCaptureSessionDTO.getStartedByUserId()))
            : null;

        User finishedByUser = createCaptureSessionDTO.getFinishedByUserId() != null
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
        return foundCaptureSession.isPresent() ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #id)")
    public void undelete(UUID id) {
        CaptureSession entity =
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
        CaptureSession captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("Capture Session: " + id));

        captureSession.setStartedByUser(
            ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getAppAccess().getUser()
        );
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
        CaptureSession captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(captureSessionId)
            .orElseThrow(() -> new NotFoundException("Capture Session: " + captureSessionId));

        log.info("Stopping capture session {} with status {}", captureSessionId, status);
        captureSession.setStatus(status);

        UUID userId = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getUserId();

        switch (status) {
            case PROCESSING -> {
                User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User: " + userId));

                captureSession.setFinishedByUser(user);
                captureSession.setFinishedAt(Timestamp.from(Instant.now()));
            }
            case RECORDING_AVAILABLE -> {
                CreateRecordingDTO recording = new CreateRecordingDTO();
                recording.setId(recordingId);
                recording.setCaptureSessionId(captureSessionId);
                recording.setVersion(1);
                try {
                    recording.setFilename(azureFinalStorageService.getMp4FileName(recordingId.toString()));
                } catch (Exception e) {
                    log.error("Failed to get recording filename for capture session {}", captureSessionId);
                }
                recordingService.upsert(recording);
                auditService.upsert(createStopAudit(captureSessionId), userId);
            }
            case NO_RECORDING, FAILURE ->
                auditService.upsert(createStopAudit(captureSessionId), userId);
            default -> {
            }
        }
        captureSessionRepository.saveAndFlush(captureSession);
        return new CaptureSessionDTO(captureSession);
    }

    @Transactional
    public CaptureSessionDTO setCaptureSessionStatus(UUID captureSessionId, RecordingStatus status) {
        CaptureSession captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(captureSessionId)
            .orElseThrow(() -> new NotFoundException("Capture Session: " + captureSessionId));
        captureSession.setStatus(status);
        captureSessionRepository.save(captureSession);
        return new CaptureSessionDTO(captureSession);
    }

    @Transactional
    public List<CaptureSessionDTO> findAllPastIncompleteCaptureSessions() {
        return captureSessionRepository.findAllPastIncompleteCaptureSessions(Timestamp.from(Instant.now())).stream()
            .map(CaptureSessionDTO::new)
            .toList();
    }

    private CreateAuditDTO createStopAudit(UUID captureSessionId) {
        CreateAuditDTO audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setTableName("capture_sessions");
        audit.setTableRecordId(captureSessionId);
        audit.setSource(AuditLogSource.AUTO);
        audit.setCategory("CaptureSession");
        audit.setActivity("Stop");
        audit.setFunctionalArea("API");
        audit.setAuditDetails(null);
        return audit;
    }
}
