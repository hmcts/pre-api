package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;

import java.sql.Time;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;


@Data
@NoArgsConstructor
@Schema(description = "CaptureSessionReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CaptureSessionReportDTO {

    @Schema(
        description = "CaptureSessionDate",
        implementation = LocalDate.class
    )
    private String date;

    @Schema(description = "CaptureSessionStartTime")
    private String startTime;

    @Schema(description = "CaptureSessionEndTime")
    private String endTime;

    @Schema(description = "CaptureSessionDuration")
    private Duration duration;

    @Schema(description = "CaptureSessionCaseReference")
    private String caseReference;

    @Schema(description = "CaptureSessionId")
    private UUID id;

    @Schema(description = "CaptureSessionCourtName")
    private String court;

    @Schema(description = "CaptureSessionRegionName")
    private String region;

    public CaptureSessionReportDTO(CaptureSession entity) {
    }
}
