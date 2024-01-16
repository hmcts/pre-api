package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateCaptureSessionDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateCaptureSessionDTO {
    @Schema(description = "CreateCaptureSessionId")
    private UUID id;

    @Schema(description = "CreateCaptureSessionBookingId")
    private UUID bookingId;

    @Schema(description = "CreateCaptureSessionOrigin")
    private RecordingOrigin origin;

    @Schema(description = "CreateCaptureSessionIngestAddress")
    private String ingestAddress;

    @Schema(description = "CreateCaptureSessionLiveOutputURL")
    private String liveOutputUrl;

    @Schema(description = "CreateCaptureSessionStartedAt")
    private Timestamp startedAt;

    @Schema(description = "CreateCaptureSessionStartedByUserId")
    private UUID startedByUserId;

    @Schema(description = "CreateCaptureSessionFinishedAt")
    private Timestamp finishedAt;

    @Schema(description = "CreateCaptureSessionFinishedByUserId")
    private UUID finishedByUserId;

    @Schema(description = "CreateCaptureSessionStatus")
    private RecordingStatus status;

    public CreateCaptureSessionDTO(CaptureSession captureSession) {
        this.id = captureSession.getId();
        this.bookingId = captureSession.getBooking().getId();
        this.origin = captureSession.getOrigin();
        this.ingestAddress = captureSession.getIngestAddress();
        this.liveOutputUrl = captureSession.getLiveOutputUrl();
        this.startedAt = captureSession.getStartedAt();
        this.startedByUserId = captureSession.getStartedByUser().getId();
        this.finishedAt = captureSession.getFinishedAt();
        this.finishedByUserId = captureSession.getFinishedByUser().getId();
        this.status = captureSession.getStatus();
    }
}
