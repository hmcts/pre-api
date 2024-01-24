package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;

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
    public void deleteCascade(Booking booking) {
        captureSessionRepository
            .findAllByBookingAndDeletedAtIsNull(booking)
            .forEach(recordingService::deleteCascade);
        captureSessionRepository.deleteAllByBooking(booking);
    }
}
