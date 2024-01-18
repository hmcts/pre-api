package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Invite;

import java.sql.Timestamp;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "InviteDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InviteDTO extends CreateInviteDTO {
    @Schema (description = "InviteId")
    private UUID id;

    @Schema(description = "InviteFirstName")
    private String firstName;

    @Schema(description = "InviteLastName")
    private String lastName;

    @Schema(description = "InviteEmail")
    private String email;

    @Schema(description = "InviteOrganisation")
    private String organisation;

    @Schema(description = "InvitePhone")
    private String phone;

    @Schema(description = "InviteCode")
    private String code;

    @Schema(description = "InvitedCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "InvitedModifiedAt")
    private Timestamp modifiedAt;

    public InviteDTO(Invite invite) {
        super(invite);
        this.createdAt = invite.getCreatedAt();
        this.modifiedAt = invite.getModifiedAt();
    }
}

