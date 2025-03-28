package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NotPastDateValidatorTest {
    private NotPastDateValidator validator;
    private ConstraintValidatorContext constraintValidatorContext;

    @BeforeEach
    void setUp() {
        validator = new NotPastDateValidator();
        constraintValidatorContext = null;
    }

    @DisplayName("Should return false when value is null")
    @Test
    void isValidWithDateNull() {
        assertFalse(validator.isValid(null, constraintValidatorContext));
    }

    @DisplayName("Should return false when value is in the past")
    @Test
    void isValidWithDateInPast() {
        ZonedDateTime pastDateTime = ZonedDateTime.now(ZoneId.of("Europe/London"))
            .minusDays(1);
        Timestamp pastTimestamp = Timestamp.from(pastDateTime.toInstant());
        assertFalse(validator.isValid(pastTimestamp, constraintValidatorContext));
    }

    @DisplayName("Should return true when value is not in the past")
    @Test
    void isValidWithDateToday() {
        ZonedDateTime currentDateTime = ZonedDateTime.now(ZoneId.of("Europe/London"));
        Timestamp currentTimestamp = Timestamp.from(currentDateTime.toInstant());
        assertTrue(validator.isValid(currentTimestamp, constraintValidatorContext));
    }

    @DisplayName("Should return true when value is in the future")
    @Test
    void givenFutureDateReturnsTrue() {
        ZonedDateTime futureDateTime = ZonedDateTime.now(ZoneId.of("Europe/London"))
            .plusDays(1);
        Timestamp futureTimestamp = Timestamp.from(futureDateTime.toInstant());
        assertTrue(validator.isValid(futureTimestamp, constraintValidatorContext));
    }
}
