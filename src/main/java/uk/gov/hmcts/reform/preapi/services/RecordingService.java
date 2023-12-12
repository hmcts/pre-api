package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareRecording;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.repositories.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecordingService {

    private final RecordingRepository recordingRepository;
    private final BookingRepository bookingRepository;
    private final CaptureSessionRepository captureSessionRepository;
    private final ShareRecordingRepository shareRecordingRepository;
    private final UserRepository userRepository;

    @Autowired
    public RecordingService(RecordingRepository recordingRepository, BookingRepository bookingRepository,
                            CaptureSessionRepository captureSessionRepository,
                            ShareRecordingRepository shareRecordingRepository,
                            UserRepository userRepository) {
        this.recordingRepository = recordingRepository;
        this.bookingRepository = bookingRepository;
        this.captureSessionRepository = captureSessionRepository;
        this.shareRecordingRepository = shareRecordingRepository;
        this.userRepository = userRepository;
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
            .orElseThrow(() -> new NotFoundException("RecordingDTO: " + recordingId));
    }

    @Transactional
    public List<RecordingDTO> findAllByBookingId(UUID bookingId, UUID captureSessionId, UUID parentRecordingId) {
        checkBookingValid(bookingId);

        return recordingRepository
            .searchAllBy(
                bookingId,
                captureSessionId,
                parentRecordingId
            ).stream()
            .map(RecordingDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(UUID bookingId, CreateRecordingDTO createRecordingDTO) {
        checkBookingValid(bookingId);

        var recording = recordingRepository.findById(createRecordingDTO.getId());

        if (recording.isPresent() && recording.get().isDeleted()) {
            throw new ResourceInDeletedStateException("RecordingDTO", createRecordingDTO.getId().toString());
        }

        var isUpdate = recording.isPresent();

        var captureSession = captureSessionRepository.findByIdAndBooking_IdAndDeletedAtIsNull(
            createRecordingDTO.getCaptureSessionId(),
            bookingId
        );

        if (!isUpdate && captureSession.isEmpty()) {
            throw new NotFoundException("CaptureSession: " + createRecordingDTO.getCaptureSessionId());
        }

        var recordingEntity = recording.orElse(new Recording());
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

        recordingRepository.deleteById(recordingId);
    }

    @Transactional
    public UpsertResult shareRecordingById(UUID bookingId, ShareRecordingDTO shareRecordingDTO) {
        checkBookingValid(bookingId);
        checkRecordingValid(shareRecordingDTO.getId());

        var captureSession = captureSessionRepository.findById(shareRecordingDTO.getCaptureSessionId()).orElse(null);
        var sharedByUser = userRepository.findById(shareRecordingDTO.getSharedByUserId()).orElse(null);
        var sharedWithUser = userRepository.findById(shareRecordingDTO.getSharedWithUserId()).orElse(null);

        if (captureSession == null) {
            throw new NotFoundException("Capture Session: " + shareRecordingDTO.getCaptureSessionId());
        }
        if (sharedByUser == null) {
            throw new NotFoundException("Shared by User: " + shareRecordingDTO.getSharedByUserId());
        }
        if (sharedWithUser == null) {
            throw new NotFoundException("Shared with User: " + shareRecordingDTO.getSharedWithUserId());
        }

        var shareRecordingEntity = new ShareRecording();
        shareRecordingEntity.setId(shareRecordingDTO.getId());
        shareRecordingEntity.setCaptureSession(captureSession);
        shareRecordingEntity.setSharedBy(sharedByUser);
        shareRecordingEntity.setSharedWith(sharedWithUser);
        shareRecordingRepository.save(shareRecordingEntity);

        return UpsertResult.CREATED;
    }

    private void checkBookingValid(UUID bookingId) {
        if (!bookingRepository.existsByIdAndDeletedAtIsNull(bookingId)) {
            throw new NotFoundException("Booking: " + bookingId);
        }
    }

    private void checkRecordingValid(UUID recordingId) {
        if (!recordingRepository.existsByIdAndDeletedAtIsNull(recordingId)) {
            throw new NotFoundException("Recording: " + recordingId);
        }
    }
}
