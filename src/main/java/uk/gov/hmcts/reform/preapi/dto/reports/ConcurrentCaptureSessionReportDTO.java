package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Duration duration;

    @Schema(description = "CaptureSessionCaseReference")
    private String caseReference;

    @Schema(description = "CaptureSessionCourtName")
    private String court;

    @Schema(description = "CaptureSessionRegionName")
    private Set<RegionDTO> region;


    public ConcurrentCaptureSessionReportDTO(CaptureSession entity) {
        id = entity.getId();
        startTime = entity.getStartedAt();
        endTime = entity.getFinishedAt();
        Case caseEntity = entity.getBooking().getCaseId();
        Court courtEntity = caseEntity.getCourt();
        court = courtEntity.getName();
        caseReference = caseEntity.getReference();
        region = Stream.ofNullable(caseEntity.getCourt().getRegions())
            .flatMap(regions -> regions.stream().map(RegionDTO::new))
            .collect(Collectors.toSet());

        Stream.ofNullable(entity.getRecordings())
            .flatMap(Set::stream)
            .filter(r -> r.getVersion() == 1 && !r.isDeleted())
            .findFirst()
            .ifPresent(r -> duration = r.getDuration());
    }
}
