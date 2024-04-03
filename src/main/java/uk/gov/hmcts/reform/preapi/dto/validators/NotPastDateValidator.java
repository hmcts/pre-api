package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class NotPastDateValidator implements ConstraintValidator<NotPastDateConstraint, Timestamp> {
    @Override
    public void initialize(NotPastDateConstraint date) {
    }

    @Override
    public boolean isValid(Timestamp dateField, ConstraintValidatorContext cxt) {
        if (dateField == null) {
            return false;
        }

        var localDateField = LocalDateTime.ofInstant(dateField.toInstant(), ZoneId.of("Europe/London")).toLocalDate();
        var today = LocalDate.now();

        System.out.println(localDateField);
        System.out.println(today);

        return !localDateField.isBefore(today);
    }
}
