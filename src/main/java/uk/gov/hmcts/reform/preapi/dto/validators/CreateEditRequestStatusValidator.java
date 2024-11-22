package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

public class CreateEditRequestStatusValidator
    implements ConstraintValidator<CreateEditRequestStatusConstraint, CreateEditRequestDTO> {

    @Override
    public void initialize(CreateEditRequestStatusConstraint constraintAnnotation) {
    }

    @Override
    public boolean isValid(CreateEditRequestDTO dto, ConstraintValidatorContext cxt) {
        if (dto.getStatus() == null) {
            return true;
        }

        if (dto.getStatus() == EditRequestStatus.REJECTED && dto.getRejectionReason() == null) {
            cxt.disableDefaultConstraintViolation();
            cxt.buildConstraintViolationWithTemplate("must have rejection reason when status is REJECTED")
                .addPropertyNode("rejectionReason")
                .addConstraintViolation();
            return false;
        }

        if (dto.getStatus() == EditRequestStatus.SUBMITTED && dto.getJointlyAgreed() == null) {
            cxt.disableDefaultConstraintViolation();
            cxt.buildConstraintViolationWithTemplate("must have jointly agreed when status is SUBMITTED")
                .addPropertyNode("jointlyAgreed")
                .addConstraintViolation();
            return false;
        }

        if (dto.getStatus() == EditRequestStatus.APPROVED) {
            if (dto.getApprovedAt() == null) {
                cxt.disableDefaultConstraintViolation();
                cxt.buildConstraintViolationWithTemplate("must have approved at when status is APPROVED")
                    .addPropertyNode("approvedAt")
                    .addConstraintViolation();
                return false;
            }
            if (dto.getApprovedBy() == null) {
                cxt.disableDefaultConstraintViolation();
                cxt.buildConstraintViolationWithTemplate("must have approved by when status is APPROVED")
                    .addPropertyNode("approvedBy")
                    .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
