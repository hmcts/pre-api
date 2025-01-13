package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

@Data
@NoArgsConstructor
@Schema(description = "SharedReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SharedReportDTO {

    @Schema(description = "SharedReportShareDate")
    private String shareDate;

    @Schema(description = "SharedReportShareTime")
    private String shareTime;

    @Schema(description = "SharedReportShareTimezone")
    private String timezone;

    @Schema(description = "SharedReportSharedWith")
    private String sharedWith;

    @Schema(description = "SharedReportSharedWithFullName")
    private String sharedWithFullName;

    @Schema(description = "SharedReportGrantedBy")
    private String grantedBy;

    @Schema(description = "SharedReportGrantedByFullName")
    private String grantedByFullName;

    @Schema(description = "SharedReportCaseReference")
    private String caseReference;

    @Schema(description = "SharedReportCourtName")
    private String courtName;

    @Schema(description = "SharedReportCourtCounty")
    private String county;

    @Schema(description = "SharedReportCourtPostcode")
    private String postcode;

    @Schema(description = "SharedReportRegions")
    private String region;

    public SharedReportDTO(ShareBooking shareBooking) {
        shareDate = DateTimeUtils.formatDate(shareBooking.getCreatedAt());
        shareTime = DateTimeUtils.formatTime(shareBooking.getCreatedAt());
        timezone = DateTimeUtils.getTimezoneAbbreviation(shareBooking.getCreatedAt());

        sharedWith = shareBooking.getSharedWith().getEmail();
        sharedWithFullName = shareBooking.getSharedWith().getFullName();
        grantedBy = shareBooking.getSharedBy().getEmail();
        grantedByFullName = shareBooking.getSharedBy().getFullName();

        var caseEntity = shareBooking.getBooking().getCaseId();
        var courtEntity = caseEntity.getCourt();
        caseReference = caseEntity.getReference();
        courtName = courtEntity.getName();
        county = courtEntity.getCounty();
        postcode = courtEntity.getPostcode();
        region = courtEntity.getRegions()
            .stream()
            .findFirst()
            .map(Region::getName)
            .orElse(null);
    }
}
