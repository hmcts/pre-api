package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "UserDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserDTO {
    @Schema(description = "UserId")
    private UUID id;

    @Schema(description = "UserFirstName")
    private String firstName;

    @Schema(description = "UserLastName")
    private String lastName;

    @Schema(description = "UserEmail")
    private String email;

    @Schema(description = "UserPhoneNumber")
    private String phoneNumber;

    @Schema(description = "UserOrganisation")
    private String organisation;

    @Schema(description = "UserCourt")
    private CourtDTO court;

    @Schema(description = "UserRole")
    private RoleDTO role;

    @Schema(description = "UserCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "UserModifiedAt")
    private Timestamp modifiedAt;

    @Schema(description = "UserDeletedAt")
    private Timestamp deletedAt;

    public UserDTO(AppAccess access) {
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
    }
}
