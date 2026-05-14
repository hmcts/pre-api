package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.opencsv.bean.CsvBindByName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import uk.gov.hmcts.reform.preapi.entities.AppAccessCourtType;

@Data
@Schema(description = "UserAccessReport")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserAccessReportDTO {

    @CsvBindByName(column = "First name")
    @Schema(description = "UserReportFirstName")
    private String firstName;

    @CsvBindByName(column = "Last name")
    @Schema(description = "UserReportLastName")
    private String lastName;

    @CsvBindByName(column = "Primary email")
    @Schema(description = "UserPrimaryEmail")
    private String primaryEmail;

    @CsvBindByName(column = "Additional email")
    @Schema(description = "UserAdditionalEmail")
    private String additionalEmail;

    @CsvBindByName(column = "Court name")
    @Schema(description = "CourtName")
    private String courtName;

    @CsvBindByName(column = "Access type")
    @Schema(description = "AccessType")
    private String accessType;

    @CsvBindByName(column = "Access role")
    @Schema(description = "UserReportRoleName")
    private String roleName;

    @CsvBindByName(column = "Active")
    @Schema(description = "UserReportActive")
    private String active;

    private UserAccessReportDTO() {
        throw new IllegalArgumentException("UserAccessReportDTO must be instantiated with fields");
    }

    public UserAccessReportDTO(String firstName, String lastName,
                               String primaryEmail, String additionalEmail,
                               String courtName, boolean isDefaultCourt,
                               String roleName, boolean active) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.primaryEmail = primaryEmail;
        this.additionalEmail = additionalEmail;
        this.courtName = courtName;
        this.roleName = roleName;

        if (isDefaultCourt) {
            this.accessType = AppAccessCourtType.PRIMARY.accessType;
        } else {
            this.accessType = AppAccessCourtType.SECONDARY.accessType;
        }

        if (active) {
            this.active = "Active";
        } else {
            this.active = "Inactive";
        }
    }
}
