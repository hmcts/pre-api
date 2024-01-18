package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Invite;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "InviteDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InviteDTO extends CreateInviteDTO {
    @Schema(description = "InviteCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "InvitedModifiedAt")
    private Timestamp modifiedAt;

    public InviteDTO(Invite invite) {
        super(invite);
        this.createdAt = invite.getCreatedAt();
        this.modifiedAt = invite.getModifiedAt();
    }
}

