package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.RecordingReencodeJob;
import uk.gov.hmcts.reform.preapi.enums.ReencodeJobStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.RecordingReencodeJobRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RecordingReencodeJobService {
    private final RecordingReencodeJobRepository recordingReencodeJobRepository;
    private final RecordingRepository recordingRepository;
    private final MigrationRecordRepository migrationRecordRepository;
    private final String sourceContainer;
    private final String blobSuffix;

    @Autowired
    public RecordingReencodeJobService(RecordingReencodeJobRepository recordingReencodeJobRepository,
                                       RecordingRepository recordingRepository,
                                       MigrationRecordRepository migrationRecordRepository,
                                       @Value("${tasks.vf-recording-reencode.source-container}") String sourceContainer,
                                       @Value("${tasks.vf-recording-reencode.blob-suffix}") String blobSuffix) {
        this.recordingReencodeJobRepository = recordingReencodeJobRepository;
        this.recordingRepository = recordingRepository;
        this.migrationRecordRepository = migrationRecordRepository;
        this.sourceContainer = sourceContainer;
        this.blobSuffix = blobSuffix;
    }

    @Transactional
    public int queueJobs(List<UUID> recordingIds) {
        int queued = 0;
        for (UUID recordingId : recordingIds) {
            if (recordingReencodeJobRepository.existsByRecordingIdAndStatusIn(
                recordingId,
                List.of(ReencodeJobStatus.PENDING, ReencodeJobStatus.PROCESSING)
            )) {
                log.info("Active re-encode job already exists for recording {}", recordingId);
                continue;
            }

            Recording recording = recordingRepository.findByIdAndDeletedAtIsNull(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording: " + recordingId));
            MigrationRecord migrationRecord = migrationRecordRepository.findFirstByRecordingId(recordingId)
                .orElseThrow(() -> new NotFoundException("MigrationRecord for recording: " + recordingId));

            String sourceBlobName = getSourceBlobName(recording, migrationRecord);

            RecordingReencodeJob job = new RecordingReencodeJob();
            job.setRecordingId(recording.getId());
            job.setCaptureSessionId(recording.getCaptureSession().getId());
            job.setMigrationRecordId(migrationRecord.getId());
            job.setSourceContainer(sourceContainer);
            job.setSourceBlobName(sourceBlobName);
            job.setReencodedBlobName(generateReencodedBlobName(sourceBlobName));
            job.setStatus(ReencodeJobStatus.PENDING);
            job.setErrorMessage(null);
            job.setStartedAt(null);
            job.setFinishedAt(null);

            recordingReencodeJobRepository.saveAndFlush(job);
            queued++;
        }
        return queued;
    }

    @Transactional(readOnly = true)
    public Optional<RecordingReencodeJob> getNextPendingJob() {
        return recordingReencodeJobRepository.findFirstByStatusOrderByCreatedAt(ReencodeJobStatus.PENDING);
    }

    @Transactional(noRollbackFor = Exception.class)
    public RecordingReencodeJob markAsProcessing(UUID jobId) {
        RecordingReencodeJob job = recordingReencodeJobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("RecordingReencodeJob: " + jobId));

        if (job.getStatus() != ReencodeJobStatus.PENDING) {
            throw new ResourceInWrongStateException(
                RecordingReencodeJob.class.getSimpleName(),
                job.getId().toString(),
                job.getStatus().toString(),
                ReencodeJobStatus.PENDING.toString()
            );
        }

        job.setStatus(ReencodeJobStatus.PROCESSING);
        job.setStartedAt(Timestamp.from(Instant.now()));
        job.setErrorMessage(null);
        return recordingReencodeJobRepository.saveAndFlush(job);
    }

    @Transactional
    public void markAsComplete(UUID jobId) {
        RecordingReencodeJob job = recordingReencodeJobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("RecordingReencodeJob: " + jobId));
        job.setStatus(ReencodeJobStatus.COMPLETE);
        job.setFinishedAt(Timestamp.from(Instant.now()));
        job.setErrorMessage(null);
        recordingReencodeJobRepository.saveAndFlush(job);
    }

    @Transactional
    public void markAsError(UUID jobId, String errorMessage) {
        RecordingReencodeJob job = recordingReencodeJobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("RecordingReencodeJob: " + jobId));
        job.setStatus(ReencodeJobStatus.ERROR);
        job.setFinishedAt(Timestamp.from(Instant.now()));
        job.setErrorMessage(errorMessage);
        recordingReencodeJobRepository.saveAndFlush(job);
    }

    protected String generateReencodedBlobName(String sourceBlobName) {
        int extensionIndex = sourceBlobName.lastIndexOf('.');
        if (extensionIndex < 0) {
            return sourceBlobName + blobSuffix;
        }
        return sourceBlobName.substring(0, extensionIndex)
            + blobSuffix
            + sourceBlobName.substring(extensionIndex);
    }

    private String getSourceBlobName(Recording recording, MigrationRecord migrationRecord) {
        if (migrationRecord.getFileName() != null && !migrationRecord.getFileName().isBlank()) {
            return migrationRecord.getFileName();
        }
        if (recording.getFilename() != null && !recording.getFilename().isBlank()) {
            return recording.getFilename();
        }
        throw new NotFoundException("Source blob name for recording: " + recording.getId());
    }
}
