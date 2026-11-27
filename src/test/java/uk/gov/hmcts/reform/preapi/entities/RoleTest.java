package uk.gov.hmcts.reform.preapi.entities;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleTest {

    @Test
    void shouldReturnRoleNameInAuditDetails() {
        Role role = new Role();
        role.setName("Level 1");

        Map<String, Object> details = role.getDetailsForAudit();

        assertEquals("Level 1", details.get("roleName"));
        assertEquals(1, details.size());
    }

    @Test
    void shouldSortByPriorityFirst() {
        Role level2 = role("Level 2");
        Role superUser = role(" super user ");
        Role level1 = role("Level 1");
        Role other = role("Zeta");

        List<Role> roles = new ArrayList<>(List.of(level2, other, level1, superUser));
        roles.sort(Role.BY_NAME_PRIORITY);

        assertEquals(" super user ", roles.get(0).getName());
        assertEquals("Level 1", roles.get(1).getName());
        assertEquals("Level 2", roles.get(2).getName());
        assertEquals("Zeta", roles.get(3).getName());
    }

    @Test
    void shouldSortOtherRolesAlphabeticallyIgnoringCase() {
        Role zeta = role("zeta");
        Role beta = role("Beta");
        Role alpha = role("alpha");

        List<Role> roles = new ArrayList<>(List.of(zeta, beta, alpha));
        roles.sort(Role.BY_NAME_PRIORITY);

        assertEquals("alpha", roles.get(0).getName());
        assertEquals("Beta", roles.get(1).getName());
        assertEquals("zeta", roles.get(2).getName());
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
