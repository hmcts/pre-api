package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;

import java.util.Set;

public class PortalAppAccessValidator
    implements ConstraintValidator<PortalAppAccessConstraint, Set<CreateAppAccessDTO>> {

    private final RoleRepository roleRepository;

    private static final String PORTAL_ROLE_NAME = "Level 3";

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
            if (a.getDefaultCourt() ==  null) {
                a.setDefaultCourt(true);
            }
        });

        return access.size() <= 1 || (
            access
                .stream()
                .filter(CreateAppAccessDTO::getDefaultCourt)
                .noneMatch(this::isRolePortal)
            && access
                .stream()
                .filter(a -> !a.getDefaultCourt())
                .noneMatch(this::isRolePortal));
    }

    private boolean isRolePortal(CreateAppAccessDTO dto) {
        return dto.getRoleId() != null
            && roleRepository.findById(dto.getRoleId()).map(r -> r.getName().equals(PORTAL_ROLE_NAME)).orElse(false);
    }
}
