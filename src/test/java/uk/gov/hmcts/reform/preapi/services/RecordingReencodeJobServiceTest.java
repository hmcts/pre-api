package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.batch.repositories.MigrationRecordRepository;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.RecordingReencodeJob;
import uk.gov.hmcts.reform.preapi.enums.ReencodeJobStatus;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.RecordingReencodeJobRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingReencodeJobServiceTest {

    @Mock
    private RecordingReencodeJobRepository recordingReencodeJobRepository;

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private MigrationRecordRepository migrationRecordRepository;

    private RecordingReencodeJobService underTest;

    @BeforeEach
    void setUp() {
        underTest = new RecordingReencodeJobService(
            recordingReencodeJobRepository,
            recordingRepository,
            migrationRecordRepository,
            "prod-migration-2",
            "-pre-reencoded"
        );
    }

    @Test
    @DisplayName("queueJobs should create a pending job from recording and migration record data")
    void queueJobsCreatesPendingJob() {
        UUID recordingId = UUID.randomUUID();
        UUID captureSessionId = UUID.randomUUID();
        UUID migrationRecordId = UUID.randomUUID();

        Recording recording = new Recording();
        recording.setId(recordingId);
        recording.setFilename("fallback.mp4");

        CaptureSession captureSession = new CaptureSession();
        captureSession.setId(captureSessionId);
        recording.setCaptureSession(captureSession);

        MigrationRecord migrationRecord = new MigrationRecord();
        migrationRecord.setId(migrationRecordId);
        migrationRecord.setFileName("source.mp4");

        when(recordingReencodeJobRepository.existsByRecordingIdAndStatusIn(any(), any())).thenReturn(false);
        when(recordingRepository.findByIdAndDeletedAtIsNull(recordingId)).thenReturn(Optional.of(recording));
        when(migrationRecordRepository.findFirstByRecordingId(recordingId)).thenReturn(Optional.of(migrationRecord));

        int queued = underTest.queueJobs(List.of(recordingId));

        assertThat(queued).isEqualTo(1);

        ArgumentCaptor<RecordingReencodeJob> captor = ArgumentCaptor.forClass(RecordingReencodeJob.class);
        verify(recordingReencodeJobRepository).saveAndFlush(captor.capture());

        RecordingReencodeJob savedJob = captor.getValue();
        assertThat(savedJob.getRecordingId()).isEqualTo(recordingId);
        assertThat(savedJob.getCaptureSessionId()).isEqualTo(captureSessionId);
        assertThat(savedJob.getMigrationRecordId()).isEqualTo(migrationRecordId);
        assertThat(savedJob.getSourceContainer()).isEqualTo("prod-migration-2");
        assertThat(savedJob.getSourceBlobName()).isEqualTo("source.mp4");
        assertThat(savedJob.getReencodedBlobName()).isEqualTo("source-pre-reencoded.mp4");
        assertThat(savedJob.getStatus()).isEqualTo(ReencodeJobStatus.PENDING);
    }

    @Test
    @DisplayName("queueJobs should skip recordings that already have an active job")
    void queueJobsSkipsActiveJobs() {
        UUID recordingId = UUID.randomUUID();
        when(recordingReencodeJobRepository.existsByRecordingIdAndStatusIn(any(), any())).thenReturn(true);

        int queued = underTest.queueJobs(List.of(recordingId));

        assertThat(queued).isZero();
        verify(recordingRepository, never()).findByIdAndDeletedAtIsNull(any());
        verify(recordingReencodeJobRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("markAsProcessing should reject jobs that are not pending")
    void markAsProcessingRejectsNonPendingJobs() {
        UUID jobId = UUID.randomUUID();
        RecordingReencodeJob job = new RecordingReencodeJob();
        job.setId(jobId);
        job.setStatus(ReencodeJobStatus.COMPLETE);

        when(recordingReencodeJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> underTest.markAsProcessing(jobId))
            .isInstanceOf(ResourceInWrongStateException.class);

        verify(recordingReencodeJobRepository, never()).saveAndFlush(any());
    }
}
