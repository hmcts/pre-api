package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.validators.SanitizedStringConstraint;

import java.util.UUID;

/**
 * EXAMPLE DTO showing how to use the @SanitizedStringConstraint annotation
 * to protect against XSS attacks on user-provided string fields.
 *
 * This is a sample/reference implementation - not meant to be used directly.
 */
@Data
@NoArgsConstructor
@Schema(description = "ExampleDTO - Shows how to apply input sanitization")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressWarnings("unused") // This is an example/reference implementation
public class ExampleSanitizedDTO {

    @NotNull
    @Schema(description = "Example ID")
    private UUID id;

    @NotNull
    @Size(max = 100)
    @SanitizedStringConstraint // Ensures no HTML/scripts can be injected
    @Schema(description = "Example Name - Will be validated for XSS attacks")
    private String name;

    @Size(max = 500)
    @SanitizedStringConstraint(message = "description contains potentially malicious HTML content")
    @Schema(description = "Example Description - Strictly sanitized (strict by default)")
    private String description;

    @Size(max = 1000)
    @SanitizedStringConstraint(allowBasicFormatting = true) // Allow <b>, <i>, <p>, <br> etc.
    @Schema(description = "Example Notes - Allows basic formatting")
    private String notes;
}

