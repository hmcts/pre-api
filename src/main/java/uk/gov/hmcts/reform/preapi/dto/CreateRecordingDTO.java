package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.sql.Time;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateRecordingDTO {
    UUID id;
    UUID captureSessionId;
    UUID parentRecordingId;
    Integer version;
    String url; // is this not needed now as it's different for every user?
    String filename;
    Time duration;
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
