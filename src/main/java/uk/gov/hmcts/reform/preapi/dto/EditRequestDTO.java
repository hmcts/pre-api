package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EditRequestDTO {
    private UUID id;
    private RecordingDTO sourceRecording;
    private String editInstruction;
    private EditRequestStatus status;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private UUID createdById;
    private Timestamp createdAt;
    private Timestamp modifiedAt;

    public EditRequestDTO(EditRequest editRequest) {
        this.id = editRequest.getId();
        this.sourceRecording = new RecordingDTO(editRequest.getSourceRecording());
        this.editInstruction = editRequest.getEditInstruction();
        this.status = editRequest.getStatus();
        this.startedAt = editRequest.getStartedAt();
        this.finishedAt = editRequest.getFinishedAt();
        this.createdById = editRequest.getCreatedBy().getId();
        this.createdAt = editRequest.getCreatedAt();
        this.modifiedAt = editRequest.getModifiedAt();
    }
}
