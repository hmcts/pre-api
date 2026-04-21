package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class SanitizedStringValidatorTest {

    private SanitizedStringValidator validator;

    @Mock
    private SanitizedStringConstraint constraint;

    @Mock
    private ConstraintValidatorContext context;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        validator = new SanitizedStringValidator();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("Should return true for null value")
    void validateNullValue() {
        when(constraint.allowBasicFormatting()).thenReturn(false);
        validator.initialize(constraint);

        assertTrue(validator.isValid(null, context));
    }

    @Test
    @DisplayName("Should return true for plain text without HTML")
    void validatePlainText() {
        when(constraint.allowBasicFormatting()).thenReturn(false);
        validator.initialize(constraint);

        assertTrue(validator.isValid("Hello World", context));
    }

    @Test
    @DisplayName("Should return false for text with script tags")
    void validateScriptTag() {
        when(constraint.allowBasicFormatting()).thenReturn(false);
        validator.initialize(constraint);

        assertFalse(validator.isValid("<script>alert('XSS')</script>", context));
    }

    @Test
    @DisplayName("Should return false for text with HTML tags in strict mode")
    void validateHtmlTagsStrictMode() {
        when(constraint.allowBasicFormatting()).thenReturn(false);
        validator.initialize(constraint);

        assertFalse(validator.isValid("<b>Bold Text</b>", context));
    }

    @Test
    @DisplayName("Should return true for text with safe HTML when basic formatting allowed")
    void validateSafeHtmlWithBasicFormatting() {
        when(constraint.allowBasicFormatting()).thenReturn(true);
        validator.initialize(constraint);

        assertTrue(validator.isValid("<b>Bold</b> and <i>Italic</i>", context));
    }

    @Test
    @DisplayName("Should return false for text with dangerous HTML even with basic formatting")
    void validateDangerousHtmlWithBasicFormatting() {
        when(constraint.allowBasicFormatting()).thenReturn(true);
        validator.initialize(constraint);

        assertFalse(validator.isValid("<script>alert('XSS')</script>", context));
    }

    @Test
    @DisplayName("Should return false for text with event handlers")
    void validateEventHandlers() {
        when(constraint.allowBasicFormatting()).thenReturn(false);
        validator.initialize(constraint);

        assertFalse(validator.isValid("<img src=x onerror='alert(1)'>", context));
    }

    @Test
    @DisplayName("Should return false for text with JavaScript in href")
    void validateJavaScriptHref() {
        when(constraint.allowBasicFormatting()).thenReturn(false);
        validator.initialize(constraint);

        assertFalse(validator.isValid("<a href='javascript:alert(1)'>Click</a>", context));
    }

    @Test
    @DisplayName("Should return true for empty string")
    void validateEmptyString() {
        when(constraint.allowBasicFormatting()).thenReturn(false);
        validator.initialize(constraint);

        assertTrue(validator.isValid("", context));
    }

    @Test
    @DisplayName("Should return true, Jsoup should not consider whitespace as malicious content")
    void validateWhitespace() {
        when(constraint.allowBasicFormatting()).thenReturn(false);
        validator.initialize(constraint);

        // JSoup's text() method normalizes whitespace, so "   " becomes ""
        assertTrue(validator.isValid("   ", context));
    }

    @Test
    @DisplayName("Should return true, Jsoup should not consider whitespace as malicious content")
    void validateWhitespaceInText() {
        when(constraint.allowBasicFormatting()).thenReturn(false);
        validator.initialize(constraint);

        // JSoup's text() method normalizes whitespace, so "   " becomes ""
        assertTrue(validator.isValid(" My  Organisation  is  great ", context));
    }
}


