package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.hmcts.reform.preapi.utils.InputSanitizerUtils;

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
        return InputSanitizerUtils.isValid(value, allowBasicFormatting);
    }
}

