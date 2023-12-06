package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecordingService {

    private final RecordingRepository recordingRepository;
    private final BookingRepository bookingRepository;
    private final CaptureSessionRepository captureSessionRepository;

    @Autowired
    public RecordingService(RecordingRepository recordingRepository, BookingRepository bookingRepository,
                            CaptureSessionRepository captureSessionRepository) {
        this.recordingRepository = recordingRepository;
        this.bookingRepository = bookingRepository;
        this.captureSessionRepository = captureSessionRepository;
    }

    @Transactional
    public RecordingDTO findById(UUID bookingId, UUID recordingId) {
        checkBookingValid(bookingId);

        return recordingRepository
            .findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingId,
                bookingId
            )
            .map(RecordingDTO::new)
            .orElse(null);
    }

    @Transactional
    public List<RecordingDTO> findAllByBookingId(UUID bookingId) {
        checkBookingValid(bookingId);

        return recordingRepository
            .findAllByCaptureSession_Booking_IdAndDeletedAtIsNull(bookingId)
            .stream()
            .map(RecordingDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(UUID bookingId, RecordingDTO recordingDto) {
        if (!bookingRepository.existsByIdAndDeletedAtIsNull(bookingId)) {
            throw new NotFoundException("BookingDTO: " + bookingId);
        }

        var isUpdate = recordingRepository.existsByIdAndDeletedAtIsNull(recordingDto.getId());

        var captureSession = captureSessionRepository.findByIdAndBooking_IdAndDeletedAtIsNull(
            recordingDto.getCaptureSessionId(),
            bookingId
        );

        if (!isUpdate && captureSession.isEmpty()) {
            throw new NotFoundException("CaptureSession: " + recordingDto.getCaptureSessionId());
        }

        var recordingEntity = new Recording();
        recordingEntity.setId(recordingDto.getId());
        if (recordingDto.getParentRecordingId() != null) {
            var parentRecording = recordingRepository.findById(recordingDto.getParentRecordingId());
            if (parentRecording.isEmpty()) {
                throw new NotFoundException("Recording: " + recordingDto.getParentRecordingId());
            }
            recordingEntity.setParentRecording(parentRecording.get());
        } else {
            recordingEntity.setParentRecording(null);
        }
        recordingEntity.setVersion(recordingDto.getVersion());
        recordingEntity.setUrl(recordingDto.getUrl());
        recordingEntity.setFilename(recordingDto.getFilename());
        recordingEntity.setDuration(recordingDto.getDuration());
        recordingEntity.setEditInstruction(recordingDto.getEditInstructions());

        recordingRepository.save(recordingEntity);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    public void deleteById(UUID bookingId, UUID recordingId) {
        checkBookingValid(bookingId);

        var recording = recordingRepository.findByIdAndCaptureSession_Booking_Id(recordingId, bookingId);

        if (recording.isEmpty() || recording.get().isDeleted()) {
            throw new NotFoundException("Recording: " + recordingId);
        }

        var recordingEntity = recording.get();
        recordingEntity.setDeletedAt(Timestamp.from(Instant.now()));
        recordingRepository.save(recordingEntity);
    }

    private void checkBookingValid(UUID bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new NotFoundException("Booking: " + bookingId);
        }
    }
}
