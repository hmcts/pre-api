package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Constraint(validatedBy = AlphanumericValidator.class)
public @interface AlphanumericConstraint {
    String message() default "must be alphanumeric";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
