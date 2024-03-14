package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateInviteDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateInviteDTO {
    @Schema (description = "InviteUserId")
    @NotNull
    protected UUID userId;

    @Schema(description = "InviteFirstName")
    @NotBlank
    protected String firstName;

    @Schema(description = "InviteLastName")
    @NotBlank
    protected String lastName;

    @Schema(description = "InviteEmail")
    @NotBlank
    @Email
    protected String email;

    @Schema(description = "InviteOrganisation")
    protected String organisation;

    @Schema(description = "InvitePhone")
    protected String phone;
}

