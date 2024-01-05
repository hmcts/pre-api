package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
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
}
