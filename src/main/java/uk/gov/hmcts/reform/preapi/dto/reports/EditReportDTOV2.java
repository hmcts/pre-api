package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "EditReportDTOV2")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EditReportDTOV2 extends BaseReportDTO {

    @Schema(description = "EditReportEditDate")
    private String editDate;

    @Schema(description = "EditReportEditTime")
    private String editTime;

    @Schema(description = "EditReportEditTimezone")
    private String timezone;

    @Schema(description = "EditReportRecordingVersion")
    private int version;

    public EditReportDTOV2(Recording recordingEntity) {
        super(recordingEntity.getCaptureSession().getBooking().getCaseId());
        editDate = DateTimeUtils.formatDate(recordingEntity.getCreatedAt());
        editTime = DateTimeUtils.formatTime(recordingEntity.getCreatedAt());
        timezone = DateTimeUtils.getTimezoneAbbreviation(recordingEntity.getCreatedAt());
        version = recordingEntity.getVersion();
    }
}
