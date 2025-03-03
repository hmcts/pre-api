package uk.gov.hmcts.reform.preapi.entities;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class PermissionTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveRole() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        Permission permission = new Permission();
        permission.setName("TestPermissionName");
        entityManager.persist(permission);
        entityManager.flush();

        Permission retrievedPermission = entityManager.find(Permission.class, permission.getId());

        assertEquals(permission.getId(), retrievedPermission.getId(), "Id should match");
        assertEquals(permission.getName(), retrievedPermission.getName(), "Name should match");
    }
}
