package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "ScheduleReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ScheduleReportDTO extends BaseReportDTO {

    @Schema(description = "ScheduleReportStartedDate")
    private String scheduledDate;

    @Schema(description = "ScheduleReportBookingCreatedAt")
    private String dateOfBooking;

    @Schema(description = "ScheduleReportUserEmail")
    private String user;

    public ScheduleReportDTO(CaptureSession captureSession) {
        super(captureSession.getBooking().getCaseId());
        var booking = captureSession.getBooking();
        scheduledDate = DateTimeUtils.formatDate(booking.getScheduledFor());
        dateOfBooking = DateTimeUtils.formatDate(booking.getCreatedAt());

        if (captureSession.getStartedByUser() != null) {
            user = captureSession.getStartedByUser().getEmail();
        }
    }
}
