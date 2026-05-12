package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.AppAccessCourtType;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Schema(description = "UserAccessReport")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserAccessReportDTO {

    @Schema(description = "UserReportFirstName")
    private String firstName;

    @Schema(description = "UserReportLastName")
    private String lastName;

    @Schema(description = "UserPrimaryEmail")
    private String primaryEmail;

    @Schema(description = "UserAdditionalEmail")
    private String additionalEmail;

    @Schema(description = "CourtName")
    private String courtName;

    @Schema(description = "AccessType")
    private String accessType;

    @Schema(description = "UserReportRoleName")
    private String roleName;

    @Schema(description = "UserReportActive")
    private String active;

    @Schema(description = "UserReportLastAccess")
    private Timestamp lastAccess;

    public UserAccessReportDTO(String firstName, String lastName,
                               String primaryEmail, String additionalEmail,
                               String courtName, boolean isDefaultCourt,
                               String roleName, Timestamp lastAccess,
                               boolean active) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.primaryEmail = primaryEmail;
        this.additionalEmail = additionalEmail;
        this.courtName = courtName;
        this.lastAccess = lastAccess;
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
