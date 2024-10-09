package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.time.Instant;

public class CaseStateValidator implements ConstraintValidator<CaseStateConstraint, CreateCaseDTO> {
    @Override
    public void initialize(CaseStateConstraint constraintAnnotation) {
    }

    @Override
    public boolean isValid(CreateCaseDTO dto, ConstraintValidatorContext cxt) {
        if (dto.getState() == null) {
            return true;
        }

        if (dto.getState() == CaseState.OPEN && dto.getClosedAt() != null) {
            cxt.disableDefaultConstraintViolation();
            cxt.buildConstraintViolationWithTemplate("must be null when case state is OPEN")
                .addPropertyNode("closedAt")
                .addConstraintViolation();
            return false;
        }

        if (dto.getState() != CaseState.OPEN && dto.getClosedAt() == null) {
            cxt.disableDefaultConstraintViolation();
            cxt.buildConstraintViolationWithTemplate("must not be null when case state is " + dto.getState())
                .addPropertyNode("closedAt")
                .addConstraintViolation();
            return false;
        }

        if (dto.getState() == CaseState.CLOSED && dto.getClosedAt().toInstant().isAfter(Instant.now())) {
            cxt.disableDefaultConstraintViolation();
            cxt.buildConstraintViolationWithTemplate("must not be in the future when case state is CLOSED")
                .addPropertyNode("closedAt")
                .addConstraintViolation();
            return false;
        }

        return true;
    }
}
