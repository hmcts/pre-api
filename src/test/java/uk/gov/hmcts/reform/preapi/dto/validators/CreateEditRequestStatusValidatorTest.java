package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CreateEditRequestStatusValidatorTest {

    private CreateEditRequestStatusValidator validator;

    @BeforeEach
    public void setUp() {
        validator = new CreateEditRequestStatusValidator();
    }

    @Test
    public void isValidStatusNullTrue() {
        var dto = new CreateEditRequestDTO();
        dto.setStatus(null);
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    public void isValidRejectedRejectionReasonNullFalse() {
        var dto = new CreateEditRequestDTO();
        dto.setStatus(EditRequestStatus.REJECTED);
        dto.setRejectionReason(null);
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        assertFalse(validator.isValid(dto, context));

        verify(context).disableDefaultConstraintViolation();
        verify(builder).addPropertyNode("rejectionReason");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    public void isValidRejectedRejectionReasonNotNullTrue() {
        var dto = new CreateEditRequestDTO();
        dto.setStatus(EditRequestStatus.REJECTED);
        dto.setRejectionReason("Reason");
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    public void isValidSubmittedJointlyAgreedNullFalse() {
        var dto = new CreateEditRequestDTO();
        dto.setStatus(EditRequestStatus.SUBMITTED);
        dto.setJointlyAgreed(null);
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        assertFalse(validator.isValid(dto, context));

        verify(context).disableDefaultConstraintViolation();
        verify(builder).addPropertyNode("jointlyAgreed");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    public void isValidSubmittedJointlyAgreedNotNullTrue() {
        var dto = new CreateEditRequestDTO();
        dto.setStatus(EditRequestStatus.SUBMITTED);
        dto.setJointlyAgreed(true);
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    public void isValidApprovedApprovedAtNullFalse() {
        var dto = new CreateEditRequestDTO();
        dto.setStatus(EditRequestStatus.APPROVED);
        dto.setApprovedAt(null);
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        assertFalse(validator.isValid(dto, context));

        verify(context).disableDefaultConstraintViolation();
        verify(builder).addPropertyNode("approvedAt");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    public void isValidApprovedApprovedByNullFalse() {
        var dto = new CreateEditRequestDTO();
        dto.setStatus(EditRequestStatus.APPROVED);
        dto.setApprovedAt(Timestamp.from(Instant.now()));
        dto.setApprovedBy(null);
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        assertFalse(validator.isValid(dto, context));

        verify(context).disableDefaultConstraintViolation();
        verify(builder).addPropertyNode("approvedBy");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    public void isValidApprovedAllFieldsNotNullTrue() {
        var dto = new CreateEditRequestDTO();
        dto.setStatus(EditRequestStatus.APPROVED);
        dto.setApprovedAt(Timestamp.from(Instant.now()));
        dto.setApprovedBy("Someone");
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(validator.isValid(dto, context));
    }
}
