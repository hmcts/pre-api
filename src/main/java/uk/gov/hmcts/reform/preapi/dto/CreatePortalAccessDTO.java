package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreatePortalAccessDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreatePortalAccessDTO {
    @Schema(description = "PortalAccessId")
    @NotNull
    private UUID id;

    @Schema(description = "PortalAccessLastAccess")
    private Timestamp lastAccess;

    @Schema(description = "PortalAccessInvitedAt")
    private Timestamp invitedAt;

    @Schema(description = "PortalAccessRegisteredAt")
    private Timestamp registeredAt;

    @Schema(description = "PortalAccessTermsAcceptedAt")
    private Timestamp termsAcceptedAt;

    @Schema(description = "PortalAccessLoggedIn")
    private Timestamp loggedIn;

    public CreatePortalAccessDTO(PortalAccess access) {
        id = access.getId();
        lastAccess = access.getLastAccess();
        invitedAt = access.getInvitedAt();
        registeredAt = access.getRegisteredAt();
    }
}
