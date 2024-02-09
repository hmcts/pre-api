package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class NotPastDateValidator implements ConstraintValidator<NotPastDateConstraint, Timestamp> {
    @Override
    public void initialize(NotPastDateConstraint date) {
    }

    @Override
    public boolean isValid(Timestamp dateField, ConstraintValidatorContext cxt) {
        var midnight = Instant.now().truncatedTo(ChronoUnit.DAYS).minusMillis(1);
        return dateField != null && dateField.after(Timestamp.from(midnight));
    }
}
