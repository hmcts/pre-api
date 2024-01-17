package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Data
@NoArgsConstructor
@Schema(description = "ConcurrentCaptureSessionReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ConcurrentCaptureSessionReportDTO {

    @Schema(description = "CaptureSessionId")
    private UUID id;

    @Schema(description = "CaptureSessionStartTime")
    private Timestamp startTime;

    @Schema(description = "CaptureSessionEndTime")
    private Timestamp endTime;

    @Schema(description = "CaptureSessionDuration", implementation = String.class)
    private Duration duration;

    @Schema(description = "CaptureSessionCaseReference")
    private String caseReference;

    @Schema(description = "CaptureSessionCourtName")
    private String court;

    @Schema(description = "CaptureSessionRegionName")
    private Set<RegionDTO> region;

    public ConcurrentCaptureSessionReportDTO(Recording entity) {
        setCaptureSessionValues(entity.getCaptureSession());
        duration = entity.getDuration();
    }

    public ConcurrentCaptureSessionReportDTO(CaptureSession entity) {
        setCaptureSessionValues(entity);
    }

    private void setCaptureSessionValues(CaptureSession entity) {
        id = entity.getId();
        startTime = entity.getStartedAt();
        endTime = entity.getFinishedAt();
        var caseEntity = entity.getBooking().getCaseId();
        var courtEntity = caseEntity.getCourt();
        court = courtEntity.getName();
        caseReference = caseEntity.getReference();
        region = courtEntity
            .getRegions()
            .stream()
            .map(RegionDTO::new)
            .collect(Collectors.toSet());
    }
}
