package uk.gov.hmcts.reform.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Permission;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class PermissionTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveRole() {
        Permission permission = new Permission();
        permission.setName("TestPermissionName");
        entityManager.persist(permission);
        entityManager.flush();

        Permission retrievedPermission = entityManager.find(Permission.class, permission.getId());

        assertEquals(permission.getId(), retrievedPermission.getId(), "Id should match");
        assertEquals(permission.getName(), retrievedPermission.getName(), "Name should match");
    }
}
