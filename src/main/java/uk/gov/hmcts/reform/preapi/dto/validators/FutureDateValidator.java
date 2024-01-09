package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.sql.Timestamp;

public class FutureDateValidator implements ConstraintValidator<FutureDateConstraint, Timestamp> {
    @Override
    public void initialize(FutureDateConstraint date) {
    }

    @Override
    public boolean isValid(Timestamp dateField, ConstraintValidatorContext cxt) {
        return dateField != null && dateField.after(new Timestamp(System.currentTimeMillis()));
    }
}
