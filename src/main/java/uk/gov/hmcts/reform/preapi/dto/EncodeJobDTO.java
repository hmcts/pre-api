package uk.gov.hmcts.reform.preapi.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.EncodeJob;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
public class EncodeJobDTO {
    private UUID id;
    private UUID captureSessionId;
    private UUID recordingId;
    private String jobName;
    private EncodeTransform transform;
    private Timestamp createdAt;
    private Timestamp modifiedAt;
    private Timestamp deletedAt;

    public EncodeJobDTO(EncodeJob encodeJob) {
        this.id = encodeJob.getId();
        this.captureSessionId = encodeJob.getCaptureSession().getId();
        this.recordingId = encodeJob.getRecordingId();
        this.jobName = encodeJob.getJobName();
        this.transform = encodeJob.getTransform();
        this.createdAt = encodeJob.getCreatedAt();
        this.modifiedAt = encodeJob.getModifiedAt();
        this.deletedAt = encodeJob.getDeletedAt();
    }
}
