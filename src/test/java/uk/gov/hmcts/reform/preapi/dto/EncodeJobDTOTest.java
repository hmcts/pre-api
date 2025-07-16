package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.EncodeJob;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EncodeJobDTOTest {
    @Test
    void testEncodeJobDTOConstructor() {
        var encodeJob = new EncodeJob();
        encodeJob.setId(UUID.randomUUID());
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        encodeJob.setCaptureSession(captureSession);
        encodeJob.setRecordingId(UUID.randomUUID());
        encodeJob.setJobName("jobName");
        encodeJob.setTransform(EncodeTransform.ENCODE_FROM_INGEST);
        encodeJob.setCreatedAt(Timestamp.from(Instant.now()));
        encodeJob.setModifiedAt(Timestamp.from(Instant.now()));

        var dto = new EncodeJobDTO(encodeJob);
        assertThat(dto.getId()).isEqualTo(encodeJob.getId());
        assertThat(dto.getCaptureSessionId()).isEqualTo(encodeJob.getCaptureSession().getId());
        assertThat(dto.getRecordingId()).isEqualTo(encodeJob.getRecordingId());
        assertThat(dto.getJobName()).isEqualTo(encodeJob.getJobName());
        assertThat(dto.getTransform()).isEqualTo(encodeJob.getTransform());
        assertThat(dto.getCreatedAt()).isEqualTo(encodeJob.getCreatedAt());
        assertThat(dto.getModifiedAt()).isEqualTo(encodeJob.getModifiedAt());
    }
}
