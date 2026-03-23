package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.validators.SanitizedStringConstraint;

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
    @SanitizedStringConstraint
    protected String firstName;

    @Schema(description = "InviteLastName")
    @NotBlank
    @SanitizedStringConstraint
    protected String lastName;

    @Schema(description = "InviteEmail")
    @NotBlank
    @Email
    protected String email;

    @Schema(description = "InviteOrganisation")
    @SanitizedStringConstraint
    protected String organisation;

    @Schema(description = "InvitePhone")
    @SanitizedStringConstraint
    protected String phone; //TODO: add validation for phone number? Safe to add Sanitised here
}

