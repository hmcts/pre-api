package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.EncodeJobDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.EncodeJob;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.EncodeJobRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @deprecated
 * This belongs to the cron job solution for the problem where capture sessions get stuck in processing.
 * See <a href="https://tools.hmcts.net/jira/issues/?jql=labels%20%3D%20capture-session-interrupted-processing">Jira label</a>.
 * We have decided not to proceed with this solution and we are investigating other architectural solutions.
 * See <a href="https://justiceuk.sharepoint.com/:w:/r/sites/PreRecordedEvidenceBAUTeam/Shared%20Documents/General/Spikes/Recording%20Process%20-%20Event%20Based%20Workflow%20Spike.docx?d=wc920d7600b8446c889fd197324e7927c&csf=1&web=1&e=EuSHgB">spike</a>.
 */
@Deprecated
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
        return encodeJobRepository.findAll().stream()
            .map(EncodeJobDTO::new)
            .toList();
    }

    @Transactional
    public void upsert(EncodeJobDTO dto) {
        EncodeJob encodeJob = fromDto(dto);
        encodeJobRepository.saveAndFlush(encodeJob);
    }

    @Transactional
    public void delete(UUID id) {
        encodeJobRepository.findById(id)
            .ifPresentOrElse(
                job -> encodeJobRepository.deleteById(job.getId()),
                () -> {
                    throw new NotFoundException("EncodeJob: " + id);
                });
    }

    protected EncodeJob fromDto(EncodeJobDTO dto) {
        CaptureSession captureSession = captureSessionRepository.findByIdAndDeletedAtIsNull(dto.getCaptureSessionId())
            .orElseThrow(() -> new NotFoundException("CaptureSession: " + dto.getCaptureSessionId()));

        if (!captureSession.getStatus().equals(RecordingStatus.PROCESSING)) {
            throw new ResourceInWrongStateException(
                "CaptureSession",
                captureSession.getId().toString(),
                captureSession.getStatus().toString(),
                RecordingStatus.PROCESSING.toString()
            );
        }

        Optional<EncodeJob> optEncodeJob = encodeJobRepository.findById(dto.getId());

        EncodeJob encodeJob = optEncodeJob.orElse(new EncodeJob());
        encodeJob.setId(dto.getId());
        encodeJob.setCaptureSession(captureSession);
        encodeJob.setRecordingId(dto.getRecordingId());
        encodeJob.setJobName(dto.getJobName());
        encodeJob.setTransform(dto.getTransform());
        return encodeJob;
    }
}
