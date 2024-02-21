package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class CaptureSessionService {

    private final RecordingService recordingService;
    private final CaptureSessionRepository captureSessionRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Autowired
    public CaptureSessionService(RecordingService recordingService,
                                 CaptureSessionRepository captureSessionRepository,
                                 BookingRepository bookingRepository,
                                 UserRepository userRepository) {
        this.recordingService = recordingService;
        this.captureSessionRepository = captureSessionRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
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
        Pageable pageable
    ) {
        var until = scheduledFor.isEmpty()
            ? null
            : scheduledFor.map(t -> Timestamp.from(t.toInstant().plus(86399, ChronoUnit.SECONDS))).orElse(null);

        var auth = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication());
        var authorisedBookings = auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings();
        var authorisedCourt = auth.isAdmin() || auth.isPortalUser() ? null : auth.getCourtId();

        return captureSessionRepository
            .searchCaptureSessionsBy(
                caseReference,
                bookingId,
                origin,
                recordingStatus,
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
        var entity = captureSessionRepository.findByIdAndDeletedAtIsNull(id);
        if (entity.isEmpty()) {
            throw new NotFoundException("CaptureSession: " + id);
        }
        recordingService.deleteCascade(entity.get());
        captureSessionRepository.deleteById(id);
    }

    @Transactional
    public void deleteCascade(Booking booking) {
        captureSessionRepository
            .findAllByBookingAndDeletedAtIsNull(booking)
            .forEach(recordingService::deleteCascade);
        captureSessionRepository.deleteAllByBooking(booking);
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

    @Transactional
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #id)")
    public void undelete(UUID id) {
        var entity =
            captureSessionRepository.findById(id).orElseThrow(() -> new NotFoundException("Capture Session: " + id));
        if (!entity.isDeleted()) {
            return;
        }
        entity.setDeletedAt(null);
        captureSessionRepository.save(entity);
    }
}
