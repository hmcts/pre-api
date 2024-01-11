package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Data
@NoArgsConstructor
@Schema(description = "CaptureSessionReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CaptureSessionReportDTO {
    @Schema(description = "CaptureSessionStartTime")
    private Timestamp startTime;

    @Schema(description = "CaptureSessionEndTime")
    private Timestamp endTime;

    @Schema(description = "CaptureSessionDuration")
    private Duration duration;

    @Schema(description = "CaptureSessionCaseReference")
    private String caseReference;

    @Schema(description = "CaptureSessionId")
    private UUID id;

    @Schema(description = "CaptureSessionCourtName")
    private String court;

    @Schema(description = "CaptureSessionRegionName")
    private Set<RegionDTO> region;

    public CaptureSessionReportDTO(Recording entity) {
        var captureSession = entity.getCaptureSession();
        startTime = captureSession.getStartedAt();
        endTime = captureSession.getFinishedAt();
        duration = entity.getDuration();
        court = captureSession.getBooking().getCaseId().getCourt().getName();
        region = captureSession
            .getBooking()
            .getCaseId()
            .getCourt()
            .getRegions()
            .stream()
            .map(RegionDTO::new)
            .collect(Collectors.toSet());
    }
}
