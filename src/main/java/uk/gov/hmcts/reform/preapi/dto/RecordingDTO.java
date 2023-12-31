package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(description = "RecordingDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecordingDTO extends BaseRecordingDTO {
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
