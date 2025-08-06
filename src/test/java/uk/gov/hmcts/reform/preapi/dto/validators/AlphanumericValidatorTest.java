package uk.gov.hmcts.reform.preapi.dto.validators;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AlphanumericValidatorTest {
    @Test
    void shouldReturnTrueForValidAlphanumericString() {
        AlphanumericValidator validator = new AlphanumericValidator();
        boolean result = validator.isValid("abc123", null);
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnTrueForEmptyString() {
        AlphanumericValidator validator = new AlphanumericValidator();
        boolean result = validator.isValid("", null);
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseForStringWithSpecialCharacters() {
        AlphanumericValidator validator = new AlphanumericValidator();
        boolean result = validator.isValid("abc123!", null);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseForStringWithSpaces() {
        AlphanumericValidator validator = new AlphanumericValidator();
        boolean result = validator.isValid("abc 123", null);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseForNullValue() {
        AlphanumericValidator validator = new AlphanumericValidator();
        boolean result = validator.isValid(null, null);
        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnTrueForStringWithOnlyUppercaseAlphanumericCharacters() {
        AlphanumericValidator validator = new AlphanumericValidator();
        boolean result = validator.isValid("ABC123", null);
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnTrueForStringWithOnlyLowercaseAlphanumericCharacters() {
        AlphanumericValidator validator = new AlphanumericValidator();
        boolean result = validator.isValid("abc123", null);
        assertThat(result).isTrue();
    }
}
