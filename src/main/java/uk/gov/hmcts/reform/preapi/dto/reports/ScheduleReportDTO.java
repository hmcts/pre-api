package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

@Data
@NoArgsConstructor
@Schema(description = "ScheduleReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ScheduleReportDTO {

    @Schema(description = "ScheduleReportStartedDate")
    private String scheduledDate;

    @Schema(description = "ScheduleReportCaseReference")
    private String caseReference;

    @Schema(description = "ScheduleReportCourtName")
    private String court;

    @Schema(description = "ScheduleReportCourtCounty")
    private String county;

    @Schema(description = "ScheduleReportCourtPostcode")
    private String postcode;

    @Schema(description = "ScheduleReportCourtRegion")
    private String region;

    @Schema(description = "ScheduleReportBookingCreatedAt")
    private String dateOfBooking;

    @Schema(description = "ScheduleReportUserEmail")
    private String user;

    public ScheduleReportDTO(CaptureSession captureSession) {
        var booking = captureSession.getBooking();
        var caseEntity = booking.getCaseId();
        scheduledDate = DateTimeUtils.formatDate(booking.getScheduledFor());
        caseReference = caseEntity.getReference();

        var courtEntity = caseEntity.getCourt();
        court = courtEntity.getName();
        county = courtEntity.getCounty();
        postcode = courtEntity.getPostcode();
        region = courtEntity.getRegions().stream().findFirst().map(Region::getName).orElse(null);

        dateOfBooking = DateTimeUtils.formatDate(booking.getCreatedAt());
        if (captureSession.getStartedByUser() != null) {
            user = captureSession.getStartedByUser().getEmail();
        }
    }
}
