package uk.gov.hmcts.reform.preapi.dto.base;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "BaseUserDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BaseUserDTO {
    @Schema(description = "UserId")
    protected UUID id;

    @Schema(description = "UserFirstName")
    protected String firstName;

    @Schema(description = "UserLastName")
    protected String lastName;

    @Schema(description = "UserEmail")
    protected String email;

    @Schema(description = "UserPhoneNumber")
    protected String phoneNumber;

    @Schema(description = "UserOrganisation")
    protected String organisation;

    @Schema(description = "active")
    protected Boolean active;

    public BaseUserDTO(User user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.phoneNumber = user.getPhone();
        this.organisation = user.getOrganisation();
        this.active = user.getDeletedAt() != null;
    }
}
