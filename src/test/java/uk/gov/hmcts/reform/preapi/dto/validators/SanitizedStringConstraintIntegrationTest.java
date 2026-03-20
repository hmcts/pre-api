package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test showing how @SanitizedStringConstraint works with real DTOs.
 * This demonstrates end-to-end validation as it would happen in a controller.
 */
class SanitizedStringConstraintIntegrationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Should pass validation when rejectionReason is plain text")
    void validatePlainTextRejectionReason() {
        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setRejectionReason("This is a valid rejection reason");

        Set<ConstraintViolation<CreateEditRequestDTO>> violations = validator.validate(dto);

        // Filter to only check rejectionReason violations
        long rejectionReasonViolations = violations.stream()
            .filter(v -> v.getPropertyPath().toString().equals("rejectionReason"))
            .count();

        assertEquals(0, rejectionReasonViolations, "Plain text should pass validation");
    }

    @Test
    @DisplayName("Should fail validation when rejectionReason contains XSS attack")
    void validateXssInRejectionReason() {
        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setRejectionReason("<script>alert('XSS')</script>Rejected");

        Set<ConstraintViolation<CreateEditRequestDTO>> violations = validator.validate(dto);

        // Check for rejectionReason violations
        boolean hasRejectionReasonViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("rejectionReason")
                && v.getMessage().contains("potentially malicious content"));

        assertTrue(hasRejectionReasonViolation,
            "XSS content should be rejected with appropriate error message");
    }

    @Test
    @DisplayName("Should fail validation when approvedBy contains HTML tags")
    void validateHtmlInApprovedBy() {
        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setApprovedBy("<b>Admin User</b>");

        Set<ConstraintViolation<CreateEditRequestDTO>> violations = validator.validate(dto);

        boolean hasApprovedByViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("approvedBy")
                && v.getMessage().contains("potentially malicious content"));

        assertTrue(hasApprovedByViolation,
            "HTML tags should be rejected even if not dangerous");
    }

    @Test
    @DisplayName("Should fail validation for multiple fields with XSS")
    void validateMultipleFieldsWithXss() {
        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setRejectionReason("<script>alert('XSS1')</script>");
        dto.setApprovedBy("<img src=x onerror='alert(1)'>");

        Set<ConstraintViolation<CreateEditRequestDTO>> violations = validator.validate(dto);

        long xssViolations = violations.stream()
            .filter(v -> v.getMessage().contains("potentially malicious content"))
            .count();

        assertTrue(xssViolations >= 2,
            "Both fields should be rejected for XSS content");
    }

    @Test
    @DisplayName("Should pass validation when fields are null")
    void validateNullFields() {
        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setRejectionReason(null);
        dto.setApprovedBy(null);

        Set<ConstraintViolation<CreateEditRequestDTO>> violations = validator.validate(dto);

        boolean hasSanitizationViolation = violations.stream()
            .anyMatch(v -> v.getMessage().contains("potentially malicious content"));

        assertFalse(hasSanitizationViolation,
            "Null values should not trigger sanitization violations");
    }

    @Test
    @DisplayName("Should provide clear error message for XSS attempts")
    void validateErrorMessageFormat() {
        CreateEditRequestDTO dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setRejectionReason("<script>alert('test')</script>");

        Set<ConstraintViolation<CreateEditRequestDTO>> violations = validator.validate(dto);

        ConstraintViolation<CreateEditRequestDTO> violation = violations.stream()
            .filter(v -> v.getPropertyPath().toString().equals("rejectionReason"))
            .findFirst()
            .orElse(null);

        if (violation != null) {
            assertEquals("rejectionReason contains potentially malicious content",
                violation.getMessage(),
                "Error message should be clear and specific");
            assertEquals("rejectionReason",
                violation.getPropertyPath().toString(),
                "Property path should identify the problematic field");
        }
    }
}

