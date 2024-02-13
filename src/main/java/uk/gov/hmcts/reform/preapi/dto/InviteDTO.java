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
    @Schema(description = "InviteUserId")
    protected UUID userId;

    @Schema(description = "InviteUserFirstName")
    protected String firstName;

    @Schema(description = "InviteUserLastName")
    protected String lastName;

    @Schema(description = "InviteUserEmail")
    protected String email;

    @Schema(description = "InviteUserPhoneNumber")
    protected String phoneNumber;

    @Schema(description = "InviteUserOrganisation")
    protected String organisation;

    @Schema(description = "InvitedAt")
    protected Timestamp invitedAt;

    @Schema(description = "InviteCode")
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
