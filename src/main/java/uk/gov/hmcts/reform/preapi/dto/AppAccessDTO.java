package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.sql.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "AppAccessDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AppAccessDTO {
    @Schema(description = "AppAccessId")
    private UUID id;

    @Schema(description = "AppAccessUser")
    private UserDTO user;

    @Schema(description = "AppAccessCourt")
    private CourtDTO court;

    @Schema(description = "AppAccessRole")
    private RoleDTO role;

    @Schema(description = "AppAccessLastAccess")
    private Date lastAccess;

    @Schema(description = "AppAccessActive")
    private boolean active;

    public AppAccessDTO(AppAccess access) {
        id = access.getId();
        user = new UserDTO(access);
        court = new CourtDTO(access.getCourt());
        role = new RoleDTO(access.getRole());
        lastAccess = access.getLastAccess();
        active = access.isActive();
    }
}
