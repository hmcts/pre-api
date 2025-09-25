package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@Schema(description = "UserPrimaryCourtReportDTOV2")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserPrimaryCourtReportDTO {

    @Schema(description = "UserPrimaryCourtReportFirstName")
    private String firstName;

    @Schema(description = "UserPrimaryCourtReportLastName")
    private String lastName;

    @Schema(description = "UserPrimaryCourtReportPrimaryCourtName")
    private String primaryCourtName;

    @Schema(description = "UserPrimaryCourtReportActive")
    private String active;

    @Schema(description = "UserPrimaryCourtReportRoleName")
    private String roleName;

    @Schema(description = "UserPrimaryCourtReportLastAccess")
    private Timestamp lastAccess;

    public UserPrimaryCourtReportDTO(String firstName, String lastName, String primaryCourtName, boolean active,
                                     String roleName, Timestamp lastAccess) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.primaryCourtName = primaryCourtName;
        if (active) {
            this.active = "Active";
        } else {
            this.active = "Inactive";
        }
        this.roleName = roleName;
        this.lastAccess = lastAccess;
    }
}
