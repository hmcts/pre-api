package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecordingService {

    private final RecordingRepository recordingRepository;
    private final CaptureSessionRepository captureSessionRepository;

    @Autowired
    public RecordingService(RecordingRepository recordingRepository,
                            CaptureSessionRepository captureSessionRepository) {
        this.recordingRepository = recordingRepository;
        this.captureSessionRepository = captureSessionRepository;
    }

    @Transactional
    public RecordingDTO findById(UUID recordingId) {
        return recordingRepository
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingId
            )
            .map(RecordingDTO::new)
            .orElseThrow(() -> new NotFoundException("RecordingDTO: " + recordingId));
    }

    @Transactional
    public Page<RecordingDTO> findAll(
        UUID captureSessionId,
        UUID parentRecordingId,
        String caseReference,
        Optional<Timestamp> scheduledFor,
        Pageable pageable
    ) {
        var until = scheduledFor.isEmpty()
            ? null
            : scheduledFor.map(
                t -> Timestamp.from(t.toInstant().plus(86399, ChronoUnit.SECONDS))).orElse(null);

        return recordingRepository
            .searchAllBy(
                captureSessionId,
                parentRecordingId,
                caseReference,
                scheduledFor.orElse(null),
                until,
                pageable
            )
            .map(RecordingDTO::new);
    }

    @Transactional
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(CreateRecordingDTO createRecordingDTO) {
        var recording = recordingRepository.findById(createRecordingDTO.getId());

        if (recording.isPresent() && recording.get().isDeleted()) {
            throw new ResourceInDeletedStateException("RecordingDTO", createRecordingDTO.getId().toString());
        }

        var isUpdate = recording.isPresent();

        var captureSession = captureSessionRepository.findByIdAndDeletedAtIsNull(
            createRecordingDTO.getCaptureSessionId()
        );

        if ((!isUpdate || createRecordingDTO.getCaptureSessionId() != null) && captureSession.isEmpty()) {
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
    public void deleteById(UUID recordingId) {
        var recording = recordingRepository
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingId
            );

        if (recording.isEmpty() || recording.get().isDeleted()) {
            throw new NotFoundException("Recording: " + recordingId);
        }

        recordingRepository.deleteById(recordingId);
    }

    @Transactional
    public void deleteCascade(CaptureSession captureSession) {
        recordingRepository.deleteAllByCaptureSession(captureSession);
    }
}
