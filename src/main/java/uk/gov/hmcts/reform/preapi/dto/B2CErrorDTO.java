package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

// https://learn.microsoft.com/en-us/azure/active-directory-b2c/restful-technical-profile#returning-validation-error-message
@Data
@NoArgsConstructor
@Schema(description = "B2CErrorDTO")
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class B2CErrorDTO {

    @Schema(description = "Version")
    private String version = "1.0.0";

    @Schema(description = "Status")
    private int status;

    @Schema(description = "User Message")
    private String userMessage;
}
