package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "InviteDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InviteDTO {
    @Schema(description = "UserId")
    protected UUID userId;

    @Schema(description = "UserFirstName")
    protected String firstName;

    @Schema(description = "UserLastName")
    protected String lastName;

    @Schema(description = "UserEmail")
    protected String email;

    @Schema(description = "UserPhoneNumber")
    protected String phoneNumber;

    @Schema(description = "UserOrganisation")
    protected String organisation;

    @Schema(description = "UserInvitedAt")
    protected Timestamp invitedAt;

    @Schema(description = "UserInviteCode")
    protected String code;

    public InviteDTO(PortalAccess portalAccess) {
        var user = portalAccess.getUser();
        userId = user.getId();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        email = user.getEmail();
        phoneNumber = user.getPhone();
        organisation = user.getOrganisation();
        invitedAt = portalAccess.getInvitedAt();
        code = portalAccess.getCode();
    }
}
