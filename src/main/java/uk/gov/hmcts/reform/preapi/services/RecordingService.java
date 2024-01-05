package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
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
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingId
            )
            .map(RecordingDTO::new)
            .orElse(null);
    }

    @Transactional
    public List<RecordingDTO> findAll(UUID captureSessionId, UUID parentRecordingId) {
        return recordingRepository
            .searchAllBy(captureSessionId, parentRecordingId)
            .stream()
            .map(RecordingDTO::new)
            .collect(Collectors.toList());
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
    public void deleteById(UUID recordingId) {
        var recording = recordingRepository
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingId
            );

        if (recording.isEmpty() || recording.get().isDeleted()) {
            throw new NotFoundException("Recording: " + recordingId);
        }

        var recordingEntity = recording.get();
        recordingEntity.setDeletedAt(Timestamp.from(Instant.now()));
        recordingRepository.save(recordingEntity);
    }
}
