package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Role;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "RoleDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RoleDTO {
    @Schema(description = "RoleId")
    private UUID id;

    @Schema(description = "RoleName")
    private String name;

    @Schema(description = "RoleDescription")
    private String description;

    public RoleDTO(Role role) {
        id = role.getId();
        name = role.getName();
        description = role.getDescription();
    }
}
