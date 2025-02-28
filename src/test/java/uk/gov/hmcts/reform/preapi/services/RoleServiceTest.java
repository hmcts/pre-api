package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.entities.Permission;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RoleService.class)
public class RoleServiceTest {
    private static Role roleEntity;
    private static Permission permissionEntity;

    @MockitoBean
    private RoleRepository roleRepository;

    @Autowired
    private RoleService roleService;

    @BeforeAll
    static void setUp() {
        permissionEntity = new Permission();
        permissionEntity.setId(UUID.randomUUID());
        permissionEntity.setName("Example Permission");

        roleEntity = new Role();
        roleEntity.setId(UUID.randomUUID());
        roleEntity.setName("Example Role");
        roleEntity.setPermissions(Set.of(permissionEntity));
    }

    @DisplayName("Find all roles and return a list of models")
    @Test
    void findAllRolesSuccess() {
        when(roleRepository.findAll()).thenReturn(List.of(roleEntity));

        var models = roleService.getAllRoles();

        assertThat(models.size()).isEqualTo(1);
        assertThat(models.getFirst().getId()).isEqualTo(roleEntity.getId());
        assertThat(models.getFirst().getName()).isEqualTo(roleEntity.getName());
        assertThat(models
                       .getFirst()
                       .getPermissions()
                       .stream()
                       .toList()
                       .getFirst()
                       .getId()
        ).isEqualTo(permissionEntity.getId());
    }
}
