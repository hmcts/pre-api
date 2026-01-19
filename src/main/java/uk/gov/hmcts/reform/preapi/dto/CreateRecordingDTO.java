package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(description = "CreateRecordingDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateRecordingDTO extends BaseRecordingDTO {

    @Schema(description = "RecordingCaptureSessionId")
    @NotNull(message = "capture_session_id is required")
    protected UUID captureSessionId;

    public CreateRecordingDTO(Recording recording) {
        super();
        id = recording.getId();
        captureSessionId = recording.getCaptureSession().getId();
        if (recording.getParentRecording() != null) {
            parentRecordingId = recording.getParentRecording().getId();
        }
        version = recording.getVersion();
        filename = recording.getFilename();
        duration = recording.getDuration();
        editInstructions = recording.getEditInstruction();
    }

    public CreateRecordingDTO(RecordingDTO recordingDTO) {
        super();
        id = recordingDTO.getId();
        captureSessionId = recordingDTO.getCaptureSession().getId();
        parentRecordingId = recordingDTO.getParentRecordingId();
        version = recordingDTO.getVersion();
        filename = recordingDTO.getFilename();
        duration = recordingDTO.getDuration();
        editInstructions = recordingDTO.getEditInstructions();
    }
}
