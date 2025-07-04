package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.EncodeJobDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.EncodeJob;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.EncodeJobRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EncodeJobService.class)
public class EncodeJobServiceTest {
    @Autowired
    private EncodeJobService encodeJobService;

    @MockitoBean
    private EncodeJobRepository encodeJobRepository;

    @MockitoBean
    private CaptureSessionRepository captureSessionRepository;

    @Test
    @DisplayName("Should get all processing jobs")
    void getAllProcessingJobs() {
        var encodeJob = createEncodeJob();
        when(encodeJobRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(encodeJob));

        var result = encodeJobService.findAllProcessing();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(encodeJob.getId());
    }

    @Test
    @DisplayName("Create encode job")
    void upsertCreateSuccess() {
        var encodeJob = createEncodeJob();
        var encodeJobDto = new EncodeJobDTO(encodeJob);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId()))
            .thenReturn(Optional.of(encodeJob.getCaptureSession()));
        when(encodeJobRepository.findById(encodeJobDto.getId())).thenReturn(Optional.empty());

        encodeJobService.upsert(encodeJobDto);

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId());
        verify(encodeJobRepository, times(1)).findById(encodeJobDto.getId());
        verify(encodeJobRepository, times(1)).saveAndFlush(any(EncodeJob.class));
    }

    @Test
    @DisplayName("Create encode job with capture session not processing")
    void upsertCreateCaptureSessionNotFound() {
        var encodeJob = createEncodeJob();
        var encodeJobDto = new EncodeJobDTO(encodeJob);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(NotFoundException.class, () -> encodeJobService.upsert(encodeJobDto))
            .getMessage();
        assertThat(message).isEqualTo("Not found: CaptureSession: " + encodeJobDto.getCaptureSessionId());

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId());
    }


    @Test
    @DisplayName("Create encode job with capture session not processing")
    void upsertCreateCaptureSessionNotProcessing() {
        var encodeJob = createEncodeJob();
        encodeJob.getCaptureSession().setStatus(RecordingStatus.FAILURE);
        var encodeJobDto = new EncodeJobDTO(encodeJob);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId()))
            .thenReturn(Optional.of(encodeJob.getCaptureSession()));
        when(encodeJobRepository.findById(encodeJobDto.getId())).thenReturn(Optional.empty());

        var message = assertThrows(ResourceInWrongStateException.class, () -> encodeJobService.upsert(encodeJobDto))
            .getMessage();
        assertThat(message).isEqualTo("Resource CaptureSession("
                                          + encodeJob.getCaptureSession().getId()
                                          + ") is in a FAILURE state. Expected state is PROCESSING.");

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId());
    }

    @Test
    @DisplayName("Create encode job with encode job in deleted state")
    void upsertCreateCaptureSessionDeleted() {
        var encodeJob = createEncodeJob();
        encodeJob.setDeletedAt(Timestamp.from(Instant.now()));
        var encodeJobDto = new EncodeJobDTO(encodeJob);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId()))
            .thenReturn(Optional.of(encodeJob.getCaptureSession()));
        when(encodeJobRepository.findById(encodeJobDto.getId())).thenReturn(Optional.of(encodeJob));

        var message = assertThrows(ResourceInDeletedStateException.class, () -> encodeJobService.upsert(encodeJobDto))
            .getMessage();
        assertThat(message).isEqualTo("Resource EncodeJob("
                                          + encodeJobDto.getId()
                                          + ") is in a deleted state and cannot be updated");

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId());
    }

    @Test
    @DisplayName("Update encode job")
    void upsertUpdateSuccess() {
        var encodeJob = createEncodeJob();
        var encodeJobDto = new EncodeJobDTO(encodeJob);

        when(captureSessionRepository.findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId()))
            .thenReturn(Optional.of(encodeJob.getCaptureSession()));
        when(encodeJobRepository.findById(encodeJobDto.getId())).thenReturn(Optional.of(encodeJob));

        encodeJobService.upsert(encodeJobDto);

        verify(captureSessionRepository, times(1)).findByIdAndDeletedAtIsNull(encodeJobDto.getCaptureSessionId());
        verify(encodeJobRepository, times(1)).findById(encodeJobDto.getId());
        verify(encodeJobRepository, times(1)).saveAndFlush(any(EncodeJob.class));
    }

    private EncodeJob createEncodeJob() {
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setStatus(RecordingStatus.PROCESSING);
        var encodeJob = new EncodeJob();
        encodeJob.setId(UUID.randomUUID());
        encodeJob.setCaptureSession(captureSession);
        encodeJob.setRecordingId(UUID.randomUUID());
        encodeJob.setJobName("jobName");
        encodeJob.setTransform(EncodeTransform.ENCODE_FROM_INGEST);
        return encodeJob;
    }
}
