package uk.gov.hmcts.reform.preapi.dto.base;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public abstract class BaseUserDTO {
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
}
