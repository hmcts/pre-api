package uk.gov.hmcts.reform.preapi.dto.base;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.RoleDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.sql.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "BaseAppAccessDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BaseAppAccessDTO {
    @Schema(description = "AppAccessId")
    protected UUID id;

    @Schema(description = "AppAccessCourt")
    protected CourtDTO court;

    @Schema(description = "AppAccessRole")
    protected RoleDTO role;

    @Schema(description = "AppAccessLastAccess")
    protected Date lastAccess;

    @Schema(description = "AppAccessActive")
    protected boolean active;

    public BaseAppAccessDTO(AppAccess access) {
        id = access.getId();
        court = new CourtDTO(access.getCourt());
        role = new RoleDTO(access.getRole());
        lastAccess = access.getLastAccess();
        active = access.isActive();
    }
}
