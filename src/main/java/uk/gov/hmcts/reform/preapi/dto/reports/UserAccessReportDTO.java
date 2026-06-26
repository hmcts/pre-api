package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.opencsv.bean.CsvBindByName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
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

    @CsvBindByName(column = "Alternative email")
    @Schema(description = "UserAlternativeEmail")
    private String alternativeEmail;

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

    public UserAccessReportDTO(AppAccess access) {
        if (access == null || access.getUser() == null || access.getRole() == null
                || access.getCourt() == null) {
            throw new IllegalArgumentException("UserAccessReportDTO must be instantiated with non-null values");
        }

        this.firstName = access.getUser().getFirstName();
        this.lastName = access.getUser().getLastName();
        this.primaryEmail = access.getUser().getEmail();
        this.alternativeEmail = access.getUser().getAlternativeEmail();
        this.courtName = access.getCourt().getName();
        this.roleName = access.getRole().getName();

        if (access.isDefaultCourt()) {
            this.accessType = AppAccessCourtType.PRIMARY.accessType;
        } else {
            this.accessType = AppAccessCourtType.SECONDARY.accessType;
        }

        if (access.isActive()) {
            this.active = "Active";
        } else {
            this.active = "Inactive";
        }
    }

}
