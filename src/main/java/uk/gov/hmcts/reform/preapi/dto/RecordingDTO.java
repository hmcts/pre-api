package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static uk.gov.hmcts.reform.preapi.utils.StringTools.isBlankString;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(description = "RecordingDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecordingDTO extends BaseRecordingDTO {

    @Schema(description = "RecordingCaptureSession")
    protected CaptureSessionDTO captureSession;

    @Schema(description = "RecordingDeletedAt")
    Timestamp deletedAt;

    @Schema(description = "RecordingCreatedAt")
    Timestamp createdAt;

    @Schema(description = "RecordingCaseId")
    private UUID caseId;

    @Schema(description = "RecordingCaseReference")
    String caseReference;

    @Schema(description = "RecordingIsTestCase")
    Boolean isTestCase;

    @Schema(description = "RecordingParticipants")
    private List<ParticipantDTO> participants;

    @Schema(description = "RecordingTotalVersionCount")
    private int totalVersionCount;

    public RecordingDTO(Recording recording) {
        super();
        id = recording.getId();
        captureSession = new CaptureSessionDTO(recording.getCaptureSession());
        if (recording.getParentRecording() != null) {
            parentRecordingId = recording.getParentRecording().getId();
        }
        Set<Recording> versions = recording.getParentRecording() != null
            ? recording.getParentRecording().getRecordings()
            : recording.getRecordings();
        totalVersionCount = versions != null ? versions.size() + 1 : 1;
        version = recording.getVersion();
        filename = recording.getFilename();
        duration = recording.getDuration();
        deletedAt = recording.getDeletedAt();
        createdAt = recording.getCreatedAt();
        Case caseEntity = recording.getCaptureSession().getBooking().getCaseId();
        caseId = caseEntity.getId();
        caseReference = caseEntity.getReference();
        isTestCase = caseEntity.isTest();
        participants = Stream.ofNullable(recording.getCaptureSession().getBooking().getParticipants())
            .flatMap(participants ->
                         participants
                             .stream()
                             .filter(participant -> participant.getDeletedAt() == null)
                             .sorted(Comparator.comparing(Participant::getFirstName))
                             .map(ParticipantDTO::new))
            .collect(Collectors.toList());

        if (recording.getEditRequest() != null) {
            editInstructions = recording.getEditRequest().getEditCutInstructionsAsJson();
            editRequest = new EditRequestDTO(recording.getEditRequest());
            editStatus = editRequest.getStatus();
        }

        if (recording.getVersion() == 1) {
            editStatus = EditRequestStatus.ORIGINAL;
        }
    }

    public List<EditCutInstructionsDTO> getEditCutInstructionsLegacyProof() {
        // Default to new-style instructions
        if (this.getEditRequest() != null && !this.getEditRequest().getEditCutInstructions().isEmpty()) {
            return this.getEditRequest().getEditCutInstructions();
        }

        // For legacy edit instructions
        if (!isBlankString(this.getEditInstructions())) {
            List<EditCutInstructions> editCutInstructionsList =
                EditRequest.convertEditCutInstructionsFromJson(this.getEditInstructions());

            return EditRequestDTO.toDTO(editCutInstructionsList);
        }

        return List.of();
    }
}
