package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "RecordingParticipantsReportDTOV2")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecordingParticipantsReportDTO {

    @Schema(description = "RecordingParticipantName")
    private String participantName;

    @Schema(description = "RecordingParticipantType")
    private ParticipantType participantType;

    @Schema(description = "RecordingParticipantRecordedAt")
    private Timestamp recordedAt;

    @Schema(description = "RecordingParticipantCourtName")
    private String courtName;

    @Schema(description = "RecordingParticipantCaseReference")
    private String caseReference;

    @Schema(description = "RecordingParticipantRecordingId")
    private UUID recordingId;

    public RecordingParticipantsReportDTO(Participant participant, Recording recording) {
        participantName = participant.getFirstName() + " " + participant.getLastName();
        participantType = participant.getParticipantType();
        recordedAt = recording.getCaptureSession().getStartedAt();
        courtName = participant.getCaseId().getCourt().getName();
        caseReference = participant.getCaseId().getReference();
        recordingId = recording.getId();
    }
}
