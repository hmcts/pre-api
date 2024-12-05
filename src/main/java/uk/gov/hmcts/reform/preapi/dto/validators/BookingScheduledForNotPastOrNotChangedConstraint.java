package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = BookingScheduledForNotPastOrNotChangedValidator.class)
@Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface BookingScheduledForNotPastOrNotChangedConstraint {
    String message() default "Date must not be in the past or not be updated";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
