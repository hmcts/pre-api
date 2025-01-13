package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

@Data
@NoArgsConstructor
@Schema(description = "EditReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EditReportDTO {

    @Schema(description = "EditReportEditDate")
    private String editDate;

    @Schema(description = "EditReportEditTime")
    private String editTime;

    @Schema(description = "EditReportEditTimezone")
    private String timezone;

    @Schema(description = "EditReportRecordingVersion")
    private int version;

    @Schema(description = "EditReportCaseReference")
    private String caseReference;

    @Schema(description = "EditReportCourtName")
    private String courtName;

    @Schema(description = "EditReportCourtCounty")
    private String county;

    @Schema(description = "EditReportCourtPostcode")
    private String postcode;

    @Schema(description = "EditReportRegions")
    private String region;

    public EditReportDTO(Recording recordingEntity) {
        editDate = DateTimeUtils.formatDate(recordingEntity.getCreatedAt());
        editTime = DateTimeUtils.formatTime(recordingEntity.getCreatedAt());
        timezone = DateTimeUtils.getTimezoneAbbreviation(recordingEntity.getCreatedAt());
        version = recordingEntity.getVersion();
        var caseEntity = recordingEntity
            .getCaptureSession()
            .getBooking()
            .getCaseId();
        caseReference = caseEntity.getReference();
        var court = caseEntity.getCourt();
        courtName = court.getName();
        county = court.getCounty();
        postcode = court.getPostcode();
        region = court.getRegions()
            .stream()
            .findFirst()
            .map(Region::getName)
            .orElse(null);
    }
}
