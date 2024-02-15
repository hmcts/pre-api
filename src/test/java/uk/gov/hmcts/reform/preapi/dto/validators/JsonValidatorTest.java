package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class JsonValidatorTest {
    @Test
    void isValidWithValidJsonShouldReturnTrue() {
        var validJson = "{\"key\": \"value\"}";
        var jsonValidator = new JsonValidator();
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(jsonValidator.isValid(validJson, context));
    }

    @Test
    void isValidWithValidEmptyJsonShouldReturnTrue() {
        var validJson = "{}";
        var jsonValidator = new JsonValidator();
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(jsonValidator.isValid(validJson, context));
    }

    @Test
    void isValidWithNullShouldReturnTrue() {
        var jsonValidator = new JsonValidator();
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(jsonValidator.isValid(null, context));
    }

    @Test
    void isValidWithInvalidJsonShouldReturnFalse() {
        var invalidJson = "invalid json";
        var jsonValidator = new JsonValidator();
        var context = mock(ConstraintValidatorContext.class);

        assertFalse(jsonValidator.isValid(invalidJson, context));
    }

}
