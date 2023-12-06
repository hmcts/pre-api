package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecordingService {

    private final RecordingRepository recordingRepository;

    private final BookingRepository bookingRepository;

    @Autowired
    public RecordingService(RecordingRepository recordingRepository, BookingRepository bookingRepository) {
        this.recordingRepository = recordingRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public RecordingDTO findById(UUID bookingId, UUID recordingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new NotFoundException("BookingDTO: " + bookingId);
        }

        return recordingRepository
            .findByIdAndCaptureSession_Booking_Id(recordingId, bookingId)
            .map(RecordingDTO::new)
            .orElse(null);
    }

    @Transactional
    public List<RecordingDTO> findAllByBookingId(UUID bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new NotFoundException("BookingDTO: " + bookingId);
        }

        return recordingRepository
            .findAllByCaptureSession_Booking_IdAndDeletedAtIsNull(bookingId)
            .stream()
            .map(RecordingDTO::new)
            .collect(Collectors.toList());
    }
}
