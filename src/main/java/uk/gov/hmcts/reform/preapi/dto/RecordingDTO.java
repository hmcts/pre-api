package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "RecordingDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecordingDTO {
    @Schema(description = "RecordingId")
    UUID id;

    @Schema(description = "RecordingCaptureSessionId")
    UUID captureSessionId;

    @Schema(description = "RecordingParentRecordingId")
    UUID parentRecordingId;

    @Schema(description = "RecordingVersion")
    Integer version;

    @Schema(description = "RecordingURL")
    String url; // is this not needed now as it's different for every user?

    @Schema(description = "RecordingFilename")
    String filename;

    @Schema(description = "RecordingDuration")
    Duration duration;

    @Schema(description = "RecordingEditInstructions")
    String editInstructions;

    @Schema(description = "RecordingDeletedAt")
    Timestamp deletedAt;

    @Schema(description = "RecordingCreatedAt")
    Timestamp createdAt;

    public RecordingDTO(Recording recording) {
        id = recording.getId();
        captureSessionId = recording.getCaptureSession().getId();
        parentRecordingId = recording.getParentRecording() != null
            ? recording.getParentRecording().getId()
            : null;
        version = recording.getVersion();
        url = recording.getUrl();
        filename = recording.getFilename();
        duration = recording.getDuration();
        editInstructions = recording.getEditInstruction();
        deletedAt = recording.getDeletedAt();
        createdAt = recording.getCreatedAt();
    }
}
