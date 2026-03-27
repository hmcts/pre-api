package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseRecordingDTO;

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

    public CreateRecordingDTO(RecordingDTO recordingDTO) {
        super();
        id = recordingDTO.getId();
        captureSessionId = recordingDTO.getCaptureSession().getId();
        parentRecordingId = recordingDTO.getParentRecordingId();
        version = recordingDTO.getVersion();
        filename = recordingDTO.getFilename();
        duration = recordingDTO.getDuration();
        editRequest = recordingDTO.getEditRequest();
        editStatus = recordingDTO.getEditStatus();
        editInstructions = recordingDTO.getEditInstructions();
    }

    public CreateRecordingDTO(UUID newRecordingId,
                              String providedFileName,
                              Integer providedVersionNumber,
                              RecordingDTO recordingDTO) {
        super();
        id = newRecordingId;
        version = providedVersionNumber;
        filename = providedFileName;

        parentRecordingId = recordingDTO.getParentRecordingId() == null
            ? recordingDTO.getId()
            : recordingDTO.getParentRecordingId();
        captureSessionId = recordingDTO.getCaptureSession().getId();
        duration = recordingDTO.getDuration();
        editRequest = recordingDTO.getEditRequest();
        editStatus = recordingDTO.getEditStatus();
        editInstructions = recordingDTO.getEditInstructions();
    }
}
