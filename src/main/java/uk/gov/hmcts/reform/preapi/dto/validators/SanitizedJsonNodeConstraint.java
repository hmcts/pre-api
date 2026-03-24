package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates JsonNode keys and string values to prevent malicious content.
 */
@Documented
@Constraint(validatedBy = SanitizedJsonNodeValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface SanitizedJsonNodeConstraint {
    String message() default "contains potentially malicious content";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}


