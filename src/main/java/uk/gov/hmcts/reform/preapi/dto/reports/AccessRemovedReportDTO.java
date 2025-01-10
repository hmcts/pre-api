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
@Schema(description = "AccessRemovedReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccessRemovedReportDTO {

    @Schema(description = "AccessRemovedReportRemovedDate")
    private String removedDate;

    @Schema(description = "AccessRemovedReportRemovedTime")
    private String removedTime;

    @Schema(description = "AccessRemovedReportRemovedTimezone")
    private String removedTimezone;

    @Schema(description = "AccessRemovedReportCaseReference")
    private String caseReference;

    @Schema(description = "AccessRemovedReportCourtName")
    private String courtName;

    @Schema(description = "AccessRemovedReportCourtCounty")
    private String county;

    @Schema(description = "AccessRemovedReportCourtPostcode")
    private String postcode;

    @Schema(description = "AccessRemovedReportRegion")
    private String region;

    @Schema(description = "AccessRemovedReportUserFullName")
    private String fullName;

    @Schema(description = "AccessRemovedReportUserEmail")
    private String userEmail;

    public AccessRemovedReportDTO(ShareBooking shareBooking) {
        removedDate = DateTimeUtils.formatDate(shareBooking.getDeletedAt());
        removedTime = DateTimeUtils.formatTime(shareBooking.getDeletedAt());
        removedTimezone = DateTimeUtils.getTimezoneAbbreviation(shareBooking.getDeletedAt());

        var booking = shareBooking.getBooking();
        var courtEntity = booking.getCaseId().getCourt();
        caseReference = booking.getCaseId().getReference();
        courtName = courtEntity.getName();
        county = courtEntity.getCounty();
        postcode = courtEntity.getPostcode();
        region = courtEntity
            .getRegions()
            .stream()
            .findFirst()
            .map(Region::getName)
            .orElse(null);

        var user = shareBooking.getSharedWith();
        fullName = user.getFullName();
        userEmail = user.getEmail();
    }
}
