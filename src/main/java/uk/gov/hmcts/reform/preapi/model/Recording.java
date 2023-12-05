package uk.gov.hmcts.reform.preapi.model;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Recording {
    UUID id;
    UUID captureSessionId;
    UUID parentRecordingId;
    Integer version;
    String url;
    String filename;
    Timestamp createdAt;
    Time duration;
    String editInstructions;
    Timestamp deletedAt;

    public Recording(uk.gov.hmcts.reform.preapi.entities.Recording recording) {
        id = recording.getId();
        captureSessionId = recording.getCaptureSession().getId();
        parentRecordingId = recording.getParentRecording() != null
            ? recording.getParentRecording().getId()
            : null;
        version = recording.getVersion();
        url = recording.getUrl();
        filename = recording.getFilename();
        createdAt = recording.getCreatedAt();
        duration = recording.getDuration();
        editInstructions = recording.getEditInstruction();
        deletedAt = recording.getDeletedAt();
    }
}
