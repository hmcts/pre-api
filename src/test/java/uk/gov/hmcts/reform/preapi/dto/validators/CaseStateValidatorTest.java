package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CaseStateValidatorTest {

    private CaseStateValidator validator;

    @BeforeEach
    public void setUp() {
        validator = new CaseStateValidator();
    }

    @Test
    public void isValidStateNullTrue() {
        var dto = new CreateCaseDTO();
        dto.setState(null);
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    public void isValidOpenClosedAtNullTrue() {
        var dto = new CreateCaseDTO();
        dto.setState(CaseState.OPEN);
        dto.setClosedAt(null);
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    public void isValidOpenClosedAtFalse() {
        var dto = new CreateCaseDTO();
        dto.setState(CaseState.OPEN);
        dto.setClosedAt(Timestamp.from(Instant.now()));
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        assertFalse(validator.isValid(dto, context));

        verify(context).disableDefaultConstraintViolation();
        verify(builder).addPropertyNode("closedAt");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    public void isValidClosedClosedAtNullFalse() {
        var dto = new CreateCaseDTO();
        dto.setState(CaseState.CLOSED);
        dto.setClosedAt(null);
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        assertFalse(validator.isValid(dto, context));

        verify(context).disableDefaultConstraintViolation();
        verify(builder).addPropertyNode("closedAt");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    public void isValidClosedClosedAtTrue() {
        var dto = new CreateCaseDTO();
        dto.setState(CaseState.CLOSED);
        dto.setClosedAt(Timestamp.from(Instant.now()));
        var context = mock(ConstraintValidatorContext.class);

        assertTrue(validator.isValid(dto, context));
    }

    @Test
    public void isValidClosedClosedAtFutureFalse() {
        var dto = new CreateCaseDTO();
        dto.setState(CaseState.CLOSED);
        dto.setClosedAt(Timestamp.from(Instant.now().plusSeconds(86400)));
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);

        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
        assertFalse(validator.isValid(dto, context));

        verify(context).disableDefaultConstraintViolation();
        verify(builder).addPropertyNode("closedAt");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    public void isValidClosedClosedAtPastTrue() {
        var dto = new CreateCaseDTO();
        dto.setState(CaseState.CLOSED);
        dto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(86400)));

        var context = mock(ConstraintValidatorContext.class);

        assertTrue(validator.isValid(dto, context));
    }
}
