package uk.gov.hmcts.reform.preapi.dto.base;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
    protected UUID id;

    @Schema(description = "UserFirstName")
    @NotBlank
    protected String firstName;

    @Schema(description = "UserLastName")
    @NotBlank
    protected String lastName;

    @Schema(description = "UserEmail")
    @NotBlank
    @Email
    protected String email;

    @Schema(description = "UserEmail2")
    @Email
    protected String email2;

    @Schema(description = "UserPhoneNumber")
    protected String phoneNumber;

    @Schema(description = "UserOrganisation")
    protected String organisation;

    public BaseUserDTO(User user) {
        id = user.getId();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        email = user.getEmail();
        email2 = user.getEmail2();
        phoneNumber = user.getPhone();
        organisation = user.getOrganisation();
    }
}
