package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class CaptureSessionService {

    private final RecordingService recordingService;
    private final CaptureSessionRepository captureSessionRepository;

    @Autowired
    public CaptureSessionService(RecordingService recordingService,
                                 CaptureSessionRepository captureSessionRepository) {
        this.recordingService = recordingService;
        this.captureSessionRepository = captureSessionRepository;
    }

    @Transactional
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
            : scheduledFor.map(
                t -> Timestamp.from(t.toInstant().plus(86399, ChronoUnit.SECONDS))).orElse(null);

        return captureSessionRepository
            .searchCaptureSessionsBy(
                caseReference,
                bookingId,
                origin,
                recordingStatus,
                scheduledFor.orElse(null),
                until,
                pageable
            )
            .map(CaptureSessionDTO::new);
    }

    @Transactional
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
}
