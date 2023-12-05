package uk.gov.hmcts.reform.preapi.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.model.Recording;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.util.UUID;

@Service
public class RecordingService {

    private final RecordingRepository recordingRepository;

    private final BookingRepository bookingRepository;

    @Autowired
    public RecordingService(RecordingRepository recordingRepository, BookingRepository bookingRepository) {
        this.recordingRepository = recordingRepository;
        this.bookingRepository = bookingRepository;
    }

    public Recording getRecordingById(UUID bookingId, UUID recordingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new NotFoundException("Booking: " + bookingId);
        }

        return recordingRepository
            .findByIdAndCaptureSession_Booking_Id(recordingId, bookingId)
            .map(Recording::new)
            .orElse(null);
    }
}
