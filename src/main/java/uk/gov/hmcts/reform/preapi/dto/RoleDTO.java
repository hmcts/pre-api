package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Role;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RoleDTO {
    private UUID id;
    private String name;
    private Set<PermissionDTO> permissions;

    public RoleDTO(Role role) {
        id = role.getId();
        name = role.getName();
        permissions = Stream.ofNullable(role.getPermissions())
            .flatMap(permissions -> permissions.stream().map(PermissionDTO::new))
            .collect(Collectors.toSet());
    }
}
