package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;

import java.util.Set;

public class PrimaryCourtValidator
    implements ConstraintValidator<PrimaryCourtConstraint, Set<CreateAppAccessDTO>> {

    public static final int MAXIMUM_SECONDARY_COURTS = 4;

    @Override
    public void initialize(PrimaryCourtConstraint constraintAnnotation) {
    }

    @Override
    public boolean isValid(Set<CreateAppAccessDTO> access, ConstraintValidatorContext cxt) {
        if (access == null || access.isEmpty()) {
            return true;
        }
        // TODO remove and condense above if statement into return when @NotNull removed in CreateAppAccess
        access.forEach(a -> {
            if (a.getDefaultCourt() == null) {
                a.setDefaultCourt(true);
            }
        });

        return access.stream().noneMatch(a -> a.getDefaultCourt() == null)
                && access.stream().filter(CreateAppAccessDTO::getDefaultCourt)
                    .count() == 1
                && access.stream().filter(a -> !a.getDefaultCourt())
                    .count() <= MAXIMUM_SECONDARY_COURTS;
    }
}
