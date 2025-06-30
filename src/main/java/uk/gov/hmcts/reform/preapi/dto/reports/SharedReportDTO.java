package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "SharedReportDTOV2")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SharedReportDTO extends BaseReportDTO {

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

    @Schema(description = "SharedReportOrganisationAllocatedTo")
    private String organisationSharedWith;

    @Schema(description = "SharedReportGrantedBy")
    private String grantedBy;

    @Schema(description = "SharedReportGrantedByFullName")
    private String grantedByFullName;

    public SharedReportDTO(ShareBooking shareBooking) {
        super(shareBooking.getBooking().getCaseId());
        shareDate = DateTimeUtils.formatDate(shareBooking.getCreatedAt());
        shareTime = DateTimeUtils.formatTime(shareBooking.getCreatedAt());
        timezone = DateTimeUtils.getTimezoneAbbreviation(shareBooking.getCreatedAt());

        sharedWith = shareBooking.getSharedWith().getEmail();
        sharedWithFullName = shareBooking.getSharedWith().getFullName();
        organisationSharedWith = shareBooking.getSharedWith().getOrganisation();

        grantedBy = shareBooking.getSharedBy().getEmail();
        grantedByFullName = shareBooking.getSharedBy().getFullName();
    }
}
