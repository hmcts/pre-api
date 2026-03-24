package uk.gov.hmcts.reform.preapi.dto.validators;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SanitizedJsonNodeValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Should return true for null value")
    void validateNullValue() {
        var validator = new SanitizedJsonNodeValidator();
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(validator.isValid(null, context));
    }

    @Test
    @DisplayName("Should return true for safe nested JSON")
    void validateSafeNestedJson() throws Exception {
        var validator = new SanitizedJsonNodeValidator();
        var context = mock(ConstraintValidatorContext.class);
        var jsonNode = OBJECT_MAPPER.readTree("""
            {
              "action": "create",
              "meta": {
                "user": "name",
                "array": ["string1", "string2"]
              }
            }
            """);

        assertTrue(validator.isValid(jsonNode, context));
    }

    @Test
    @DisplayName("Should return false when key contains script tag")
    void validateUnsafeKey() throws Exception {
        var validator = new SanitizedJsonNodeValidator();
        var context = mock(ConstraintValidatorContext.class);
        var jsonNode = OBJECT_MAPPER.readTree("{\"<script>key</script>\": \"value\"}");

        assertFalse(validator.isValid(jsonNode, context));
    }

    @Test
    @DisplayName("Should return false when value contains script tag")
    void validateUnsafeValue() throws Exception {
        var validator = new SanitizedJsonNodeValidator();
        var context = mock(ConstraintValidatorContext.class);
        var jsonNode = OBJECT_MAPPER.readTree("{\"key\": \"<script>alert(1)</script>\"}");

        assertFalse(validator.isValid(jsonNode, context));
    }

    @Test
    @DisplayName("Should return false when nested value contains malicious content")
    void validateUnsafeNestedValue() throws Exception {
        var validator = new SanitizedJsonNodeValidator();
        var context = mock(ConstraintValidatorContext.class);
        var jsonNode = OBJECT_MAPPER.readTree("""
            {
              "details": [
                {"safe": "ok"},
                {"unsafe": "<img src=x onerror='alert(1)'>"}
              ]
            }
            """);

        assertFalse(validator.isValid(jsonNode, context));
    }
}

