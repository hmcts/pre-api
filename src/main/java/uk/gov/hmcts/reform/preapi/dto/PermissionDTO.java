package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Permission;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "PermissionDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PermissionDTO {
    @Schema(description = "PermissionId")
    private UUID id;
    @Schema(description = "PermissionName")
    private String name;

    public PermissionDTO(Permission permission) {
        id = permission.getId();
        name = permission.getName();
    }
}
