package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "VerifyEmailRequestDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class VerifyEmailRequestDTO {

    @Schema(description = "Email")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "VerificationCode")
    @NotBlank
    @Pattern(regexp = "^[0-9]{6}$", message = "invalid verification code")
    private String verificationCode;
}
