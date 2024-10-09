package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CaseStateValidator.class)
public @interface CaseStateConstraint {
    String message() default "invalid case state";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
