package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Invite;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateInviteDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateInviteDTO {
    @Schema (description = "InviteId")
    protected UUID id;

    @Schema(description = "InviteFirstName")
    protected String firstName;

    @Schema(description = "InviteLastName")
    protected String lastName;

    @Schema(description = "InviteEmail")
    protected String email;

    @Schema(description = "InviteOrganisation")
    protected String organisation;

    @Schema(description = "InvitePhone")
    protected String phone;

    @Schema(description = "InviteCode")
    protected String code;

    public CreateInviteDTO(Invite invite) {
        this.id = invite.getId();
        this.firstName = invite.getFirstName();
        this.lastName = invite.getLastName();
        this.email = invite.getEmail();
        this.organisation = invite.getOrganisation();
        this.phone = invite.getPhone();
        this.code = invite.getCode();
    }
}

