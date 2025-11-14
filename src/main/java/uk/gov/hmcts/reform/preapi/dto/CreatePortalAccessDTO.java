package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

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

    @Schema(description = "PortalAccessStatus")
    @NotNull
    private AccessStatus status;

    @Schema(description = "PortalAccessInvitedAt")
    private Timestamp invitedAt;

    @Schema(description = "PortalAccessRegisteredAt")
    private Timestamp registeredAt;

    public CreatePortalAccessDTO(PortalAccessDTO portalAccessDTO) {
        this.id = portalAccessDTO.getId();
        this.lastAccess = portalAccessDTO.getLastAccess();
        this.status = portalAccessDTO.getStatus();
        this.invitedAt = portalAccessDTO.getInvitedAt();
        this.registeredAt = portalAccessDTO.getRegisteredAt();
    }
}
