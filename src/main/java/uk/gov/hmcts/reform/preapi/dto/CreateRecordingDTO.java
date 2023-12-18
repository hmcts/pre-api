package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.time.Duration;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateRecordingDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateRecordingDTO {
    @Schema(description = "CreateRecordingId")
    UUID id;

    @Schema(description = "CreateRecordingCaptureSessionId")
    UUID captureSessionId;

    @Schema(description = "CreateRecordingParentRecordingId")
    UUID parentRecordingId;

    @Schema(description = "CreateRecordingVersion")
    Integer version;

    @Schema(description = "CreateRecordingURL")
    String url; // is this not needed now as it's different for every user?

    @Schema(description = "CreateRecordingFilename")
    String filename;

    @Schema(description = "CreateRecordingDuration")
    Duration duration;

    @Schema(description = "CreateRecordingEditInstructions")
    String editInstructions;

    public CreateRecordingDTO(Recording recording) {
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
    }
}
