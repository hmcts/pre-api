package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.enums.CourtAccessType;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PortalAppAccessValidatorTest {

    @MockBean
    private RoleRepository roleRepository;

    private PortalAppAccessValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        validator = new PortalAppAccessValidator(roleRepository);
    }

    @DisplayName("Should be valid when app access set is null")
    @Test
    void shouldBeValidWhenAppAccessSetIsNull() {
        assertTrue(validator.isValid(null, context));
    }

    @DisplayName("Should be valid when app access set is empty")
    @Test
    void shouldBeValidWhenAppAccessSetIsEmpty() {
        assertTrue(validator.isValid(Set.of(), context));
    }

    @DisplayName("Should handle court access type null values as PRIMARY court assignments")
    @Test
    void shouldHandleCourtAccessTypeNullValues() {
        var dto1 = createAppAccessDTO(null);

        when(roleRepository.findById(dto1.getRoleId())).thenReturn(Optional.of(createRole(false)));

        assertTrue(validator.isValid(Set.of(dto1), context));
        assertThat(dto1.getCourtAccessType()).isEqualTo(CourtAccessType.PRIMARY);

        var dto2 = createAppAccessDTO(null);
        when(roleRepository.findById(dto2.getRoleId())).thenReturn(Optional.of(createRole(true)));

        assertTrue(validator.isValid(Set.of(dto2), context));
        assertThat(dto2.getCourtAccessType()).isEqualTo(CourtAccessType.PRIMARY);
    }

    @DisplayName("Should be valid when contains one access and it is has a portal role")
    @Test
    void shouldBeValidOneAccessIsPortal() {
        var dto = createAppAccessDTO(CourtAccessType.PRIMARY);
        when(roleRepository.findById(dto.getRoleId())).thenReturn(Optional.of(createRole(true)));

        assertTrue(validator.isValid(Set.of(dto), context));
    }

    @DisplayName("Should be valid when contains one access and it is not portal role")
    @Test
    void shouldBeValidOneAccessIsNotPortal() {
        var dto = createAppAccessDTO(CourtAccessType.PRIMARY);
        when(roleRepository.findById(dto.getRoleId())).thenReturn(Optional.of(createRole(false)));

        assertTrue(validator.isValid(Set.of(dto), context));
    }

    @DisplayName("Should be valid when there are many access assignments and none have portal role")
    @Test
    void shouldBeValidManyAccessNonePortal() {
        var dto1 = createAppAccessDTO(CourtAccessType.PRIMARY);
        var dto2 = createAppAccessDTO(CourtAccessType.SECONDARY);
        when(roleRepository.findById(dto1.getRoleId())).thenReturn(Optional.of(createRole(false)));
        when(roleRepository.findById(dto2.getRoleId())).thenReturn(Optional.of(createRole(false)));

        assertTrue(validator.isValid(Set.of(dto1, dto2), context));
    }

    @DisplayName("Should be invalid when there are many access assignments and any have portal role")
    @Test
    void shouldBeInvalidManyAccessSomePortal() {
        var dto1 = createAppAccessDTO(CourtAccessType.PRIMARY);
        var dto2 = createAppAccessDTO(CourtAccessType.SECONDARY);

        when(roleRepository.findById(dto1.getRoleId())).thenReturn(Optional.of(createRole(true)));
        when(roleRepository.findById(dto2.getRoleId())).thenReturn(Optional.of(createRole(true)));
        assertFalse(validator.isValid(Set.of(dto1, dto2), context));

        when(roleRepository.findById(dto1.getRoleId())).thenReturn(Optional.of(createRole(false)));
        when(roleRepository.findById(dto2.getRoleId())).thenReturn(Optional.of(createRole(true)));

        assertFalse(validator.isValid(Set.of(dto1, dto2), context));

        when(roleRepository.findById(dto1.getRoleId())).thenReturn(Optional.of(createRole(true)));
        when(roleRepository.findById(dto2.getRoleId())).thenReturn(Optional.of(createRole(false)));

        assertFalse(validator.isValid(Set.of(dto1, dto2), context));
    }


    private CreateAppAccessDTO createAppAccessDTO(CourtAccessType type) {
        var dto = new CreateAppAccessDTO();
        dto.setId(UUID.randomUUID());
        dto.setRoleId(UUID.randomUUID());
        dto.setCourtAccessType(type);
        return dto;
    }

    private Role createRole(boolean isPortal) {
        var role = new Role();
        role.setName(isPortal ? "Level 3" : "Level 1");
        return role;
    }
}
