package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateUserDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateUserDTO {
    @Schema(description = "CreateUserId")
    private UUID id;

    @Schema(description = "CreateUserFirstName")
    private String firstName;

    @Schema(description = "CreateUserLastName")
    private String lastName;

    @Schema(description = "CreateUserEmail")
    private String email;

    @Schema(description = "CreateUserPhoneNumber")
    private String phoneNumber;

    @Schema(description = "CreateUserOrganisation")
    private String organisation;

    @Schema(description = "CreateUserCourtId")
    private UUID courtId;

    @Schema(description = "CreateUserRoleId")
    private UUID roleId;
}
