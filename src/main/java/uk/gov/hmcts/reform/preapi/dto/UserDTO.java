package uk.gov.hmcts.reform.preapi.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "UserDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserDTO extends BaseUserDTO {
    @Schema(description = "UserCourt")
    protected CourtDTO court;

    @Schema(description = "UserRole")
    protected RoleDTO role;

    @Schema(description = "UserCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "UserModifiedAt")
    private Timestamp modifiedAt;

    @Schema(description = "UserDeletedAt")
    protected Timestamp deletedAt;

    public UserDTO(AppAccess access) {
        super();
        var user = access.getUser();
        id = user.getId();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        email = user.getEmail();
        phoneNumber = user.getPhone();
        organisation = user.getOrganisation();
        createdAt = user.getCreatedAt();
        modifiedAt = user.getModifiedAt();
        court = new CourtDTO(access.getCourt());
        role = new RoleDTO(access.getRole());
        active = access.isActive();
    }
}
