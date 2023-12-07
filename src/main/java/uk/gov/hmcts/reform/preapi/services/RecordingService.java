package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
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
    public CreateRecordingDTO findById(UUID bookingId, UUID recordingId) {
        checkBookingValid(bookingId);

        return recordingRepository
            .findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingId,
                bookingId
            )
            .map(CreateRecordingDTO::new)
            .orElse(null);
    }

    @Transactional
    public List<CreateRecordingDTO> findAllByBookingId(UUID bookingId, UUID captureSessionId, UUID parentRecordingId) {
        checkBookingValid(bookingId);

        return recordingRepository
            .searchAllBy(
                bookingId,
                captureSessionId,
                parentRecordingId
            ).stream()
            .map(CreateRecordingDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(UUID bookingId, CreateRecordingDTO createRecordingDTO) {
        checkBookingValid(bookingId);

        var isUpdate = recordingRepository.existsByIdAndDeletedAtIsNull(createRecordingDTO.getId());

        var captureSession = captureSessionRepository.findByIdAndBooking_IdAndDeletedAtIsNull(
            createRecordingDTO.getCaptureSessionId(),
            bookingId
        );

        if (!isUpdate && captureSession.isEmpty()) {
            throw new NotFoundException("CaptureSession: " + createRecordingDTO.getCaptureSessionId());
        }

        var recordingEntity = new Recording();
        recordingEntity.setId(createRecordingDTO.getId());
        if (createRecordingDTO.getParentRecordingId() != null) {
            var parentRecording = recordingRepository.findById(createRecordingDTO.getParentRecordingId());
            if (parentRecording.isEmpty()) {
                throw new NotFoundException("Recording: " + createRecordingDTO.getParentRecordingId());
            }
            recordingEntity.setParentRecording(parentRecording.get());
        } else {
            recordingEntity.setParentRecording(null);
        }
        recordingEntity.setVersion(createRecordingDTO.getVersion());
        recordingEntity.setUrl(createRecordingDTO.getUrl());
        recordingEntity.setFilename(createRecordingDTO.getFilename());
        recordingEntity.setDuration(createRecordingDTO.getDuration());
        recordingEntity.setEditInstruction(createRecordingDTO.getEditInstructions());

        recordingRepository.save(recordingEntity);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    public void deleteById(UUID bookingId, UUID recordingId) {
        checkBookingValid(bookingId);

        var recording = recordingRepository
            .findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingId,
                bookingId
            );

        if (recording.isEmpty() || recording.get().isDeleted()) {
            throw new NotFoundException("Recording: " + recordingId);
        }

        var recordingEntity = recording.get();
        recordingEntity.setDeletedAt(Timestamp.from(Instant.now()));
        recordingRepository.save(recordingEntity);
    }

    private void checkBookingValid(UUID bookingId) {
        if (!bookingRepository.existsByIdAndDeletedAtIsNull(bookingId)) {
            throw new NotFoundException("Booking: " + bookingId);
        }
    }
}
