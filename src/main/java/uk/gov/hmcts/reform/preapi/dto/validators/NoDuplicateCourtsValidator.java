package uk.gov.hmcts.reform.preapi.dto.validators;


import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;

import java.util.Set;

public class NoDuplicateCourtsValidator
    implements ConstraintValidator<NoDuplicateCourtsConstraint, Set<CreateAppAccessDTO>> {

    @Override
    public void initialize(NoDuplicateCourtsConstraint constraintAnnotation) {
    }

    @Override
    public boolean isValid(Set<CreateAppAccessDTO> access, ConstraintValidatorContext cxt) {
        return access == null
            || access.isEmpty()
            || access.stream().map(CreateAppAccessDTO::getCourtId).distinct().count() == access.size();
    }
}
