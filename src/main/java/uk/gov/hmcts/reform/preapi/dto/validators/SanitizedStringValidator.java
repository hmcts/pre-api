package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.hmcts.reform.preapi.utils.InputSanitizer;

/**
 * Validator that checks if a string contains potentially malicious content.
 * This validator uses JSoup to detect HTML/script content that could lead to XSS attacks.
 */
public class SanitizedStringValidator implements ConstraintValidator<SanitizedStringConstraint, String> {

    private boolean allowBasicFormatting;

    @Override
    public void initialize(SanitizedStringConstraint constraint) {
        this.allowBasicFormatting = constraint.allowBasicFormatting();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Use @NotNull for null checks
        }

        // Check if sanitization would change the string
        // If it changes, it means there was potentially malicious content
        String sanitized = InputSanitizer.sanitize(value, allowBasicFormatting);

        return value.equals(sanitized);
    }
}

