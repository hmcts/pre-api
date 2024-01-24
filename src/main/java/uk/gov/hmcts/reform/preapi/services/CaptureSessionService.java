package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

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

    @Transactional
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
}
