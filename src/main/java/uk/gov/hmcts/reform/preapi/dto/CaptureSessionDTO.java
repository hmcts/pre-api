package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "CaptureSessionDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CaptureSessionDTO extends CreateCaptureSessionDTO {

    @Schema(description = "CaptureSessionDeletedAt")
    private Timestamp deletedAt;

    @Schema(description = "RecordingParticipants") // todo change this (might be breaking)
    private String courtName;

    @Schema(description = "CaptureSessionCaseState")
    private CaseState caseState;

    @Schema(description = "CaptureSessionCaseClosedAt")
    private Timestamp caseClosedAt;

    @Schema(description = "CaptureSessionCaseReference")
    private String caseReference;

    public CaptureSessionDTO(CaptureSession captureSession) {
        super(captureSession);
        deletedAt = captureSession.getDeletedAt();
        courtName = captureSession.getBooking().getCaseId().getCourt().getName();
        caseState = captureSession.getBooking().getCaseId().getState();
        caseClosedAt = captureSession.getBooking().getCaseId().getClosedAt();
        caseReference = captureSession.getBooking().getCaseId().getReference();
    }
}
