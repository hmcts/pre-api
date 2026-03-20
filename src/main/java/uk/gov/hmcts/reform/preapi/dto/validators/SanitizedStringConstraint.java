package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates and sanitizes string input to prevent XSS attacks.
 * This annotation can be applied to String fields to ensure they are
 * properly sanitized before processing.
 */
@Documented
@Constraint(validatedBy = SanitizedStringValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SanitizedStringConstraint {
    String message() default "contains potentially malicious content";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    /**
     * Whether to allow basic text formatting (bold, italic, etc.)
     * Default is false (strips all HTML)
     */
    boolean allowBasicFormatting() default false;
}

