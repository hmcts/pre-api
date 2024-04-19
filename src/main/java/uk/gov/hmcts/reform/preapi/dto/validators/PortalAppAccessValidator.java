package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtAccessType;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;

import java.util.Set;

public class PortalAppAccessValidator
    implements ConstraintValidator<PortalAppAccessConstraint, Set<CreateAppAccessDTO>> {

    private final RoleRepository roleRepository;

    @Autowired
    public PortalAppAccessValidator(final RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void initialize(PortalAppAccessConstraint constraintAnnotation) {
    }

    @Override
    public boolean isValid(Set<CreateAppAccessDTO> access, ConstraintValidatorContext cxt) {
        if (access == null) {
            return true;
        }
        // TODO remove and condense above if statement into return when @NotNull removed in CreateAppAccess
        access.forEach(a -> {
            if (a.getCourtAccessType() ==  null) {
                a.setCourtAccessType(CourtAccessType.PRIMARY);
            }
        });

        return access.size() <= 1 || (
            access
                .stream()
                .filter(a -> a.getCourtAccessType().equals(CourtAccessType.PRIMARY))
                .noneMatch(this::isRolePortal)
            && access
                .stream()
                .filter(a -> a.getCourtAccessType().equals(CourtAccessType.SECONDARY))
                .noneMatch(this::isRolePortal));
    }

    private boolean isRolePortal(CreateAppAccessDTO dto) {
        return roleRepository.findById(dto.getRoleId()).map(r -> r.getName().equals("Level 3")).orElse(false);
    }
}
