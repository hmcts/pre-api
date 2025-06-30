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
@Schema(description = "AccessRemovedReportDTOV2")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccessRemovedReportDTOV2 extends BaseReportDTO {

    @Schema(description = "AccessRemovedReportRemovedDate")
    private String removedDate;

    @Schema(description = "AccessRemovedReportRemovedTime")
    private String removedTime;

    @Schema(description = "AccessRemovedReportRemovedTimezone")
    private String removedTimezone;

    @Schema(description = "AccessRemovedReportUserFullName")
    private String fullName;

    @Schema(description = "AccessRemovedReportUserEmail")
    private String userEmail;

    public AccessRemovedReportDTOV2(ShareBooking shareBooking) {
        super(shareBooking.getBooking().getCaseId());
        removedDate = DateTimeUtils.formatDate(shareBooking.getDeletedAt());
        removedTime = DateTimeUtils.formatTime(shareBooking.getDeletedAt());
        removedTimezone = DateTimeUtils.getTimezoneAbbreviation(shareBooking.getDeletedAt());

        var user = shareBooking.getSharedWith();
        fullName = user.getFullName();
        userEmail = user.getEmail();
    }
}
