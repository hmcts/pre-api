package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;

import java.sql.Timestamp;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "ScheduleReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ScheduleReportDTO {

    @Schema(description = "ScheduleReportStartedAt")
    private Timestamp scheduledFor;

    @Schema(description = "ScheduleReportBookingCreatedAt")
    private Timestamp bookingCreatedAt;

    @Schema(description = "ScheduleReportCaseReference")
    private String caseReference;

    @Schema(description = "ScheduleReportUserEmail")
    private String captureSessionUser;

    @Schema(description = "ScheduleReportCourtName")
    private String court;

    @Schema(description = "ScheduleReportCourtRegions")
    private Set<RegionDTO> regions;

    public ScheduleReportDTO(CaptureSession captureSession) {
        var bookingEntity = captureSession.getBooking();
        var caseEntity = bookingEntity.getCaseId();
        scheduledFor = bookingEntity.getScheduledFor();
        bookingCreatedAt = bookingEntity.getCreatedAt();
        caseReference = caseEntity.getReference();
        if (captureSession.getStartedByUser() != null) {
            captureSessionUser = captureSession.getStartedByUser().getEmail();
        }
        court = caseEntity.getCourt().getName();
        regions = Stream.ofNullable(caseEntity.getCourt().getRegions())
            .flatMap(regions -> regions.stream().map(RegionDTO::new))
            .collect(Collectors.toSet());
    }
}
