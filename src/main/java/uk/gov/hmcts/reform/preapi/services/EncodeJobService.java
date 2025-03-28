package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.EncodeJobDTO;
import uk.gov.hmcts.reform.preapi.entities.EncodeJob;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.EncodeJobRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EncodeJobService {

    private final EncodeJobRepository encodeJobRepository;
    private final CaptureSessionRepository captureSessionRepository;

    @Autowired
    public EncodeJobService(EncodeJobRepository encodeJobRepository,
                            CaptureSessionRepository captureSessionRepository) {
        this.encodeJobRepository = encodeJobRepository;
        this.captureSessionRepository = captureSessionRepository;
    }

    @Transactional
    public List<EncodeJobDTO> findAllProcessing() {
        return encodeJobRepository.findAllByDeletedAtIsNull().stream()
            .map(EncodeJobDTO::new)
            .toList();
    }

    @Transactional
    public void upsert(EncodeJobDTO dto) {
        var encodeJob = fromDto(dto);
        encodeJobRepository.saveAndFlush(encodeJob);
    }

    @Transactional
    public void delete(UUID id) {
        var encodeJob = encodeJobRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("EncodeJob: " + id));
        encodeJob.setDeletedAt(Timestamp.from(Instant.now()));
        encodeJobRepository.saveAndFlush(encodeJob);
    }

    protected EncodeJob fromDto(EncodeJobDTO dto) {
        var captureSession = captureSessionRepository.findByIdAndDeletedAtIsNull(dto.getCaptureSessionId())
            .orElseThrow(() -> new NotFoundException("CaptureSession: " + dto.getCaptureSessionId()));

        if (!captureSession.getStatus().equals(RecordingStatus.PROCESSING)) {
            throw new ResourceInWrongStateException(
                "CaptureSession",
                captureSession.getId().toString(),
                captureSession.getStatus().toString(),
                RecordingStatus.PROCESSING.toString()
            );
        }

        var optEncodeJob = encodeJobRepository.findById(dto.getId());
        if (optEncodeJob.isPresent() && optEncodeJob.get().getDeletedAt() != null) {
            throw new ResourceInDeletedStateException("EncodeJob", dto.getId().toString());
        }

        var encodeJob = optEncodeJob.orElse(new EncodeJob());
        encodeJob.setId(dto.getId());
        encodeJob.setCaptureSession(captureSession);
        encodeJob.setRecordingId(dto.getRecordingId());
        encodeJob.setJobName(dto.getJobName());
        encodeJob.setTransform(dto.getTransform());
        return encodeJob;
    }
}
