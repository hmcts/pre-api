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
    @Schema (description = "CreateInviteId")
    private UUID id;

    @Schema(description = "CreateInviteFirstName")
    private String firstName;

    @Schema(description = "CreateInviteLastName")
    private String lastName;

    @Schema(description = "CreateInviteEmail")
    private String email;

    @Schema(description = "CreateInviteOrganisation")
    private String organisation;

    @Schema(description = "CreateInvitePhone")
    private String phone;

    @Schema(description = "CreateInviteCode")
    private String code;

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

