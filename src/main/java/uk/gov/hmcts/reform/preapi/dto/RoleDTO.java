package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Role;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "RoleDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RoleDTO {
    @Schema(description = "RoleId")
    private UUID id;

    @Schema(description = "RoleName")
    private String name;

    @Schema(description = "RolePermissions")
    private Set<PermissionDTO> permissions;

    public RoleDTO(Role role) {
        id = role.getId();
        name = role.getName();
        permissions = Stream.ofNullable(role.getPermissions())
            .flatMap(permissions -> permissions.stream().map(PermissionDTO::new))
            .collect(Collectors.toSet());
    }
}
