package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "VerifyEmailRequestDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class VerifyEmailRequestDTO {

    @Schema(description = "Email")
    private String email;

    @Schema(description = "VerificationCode")
    private String verificationCode;
}
