package uk.gov.hmcts.reform.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.Role;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class RoleTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveRole() {
        Role testRole = new Role();
        testRole.setName("TestRoleName");
        entityManager.persist(testRole);
        entityManager.flush();

        Role retrievedRole = entityManager.find(Role.class, testRole.getId());

        assertEquals(testRole.getId(), retrievedRole.getId(), "Id should match");
        assertEquals(testRole.getName(), retrievedRole.getName(), "Name should match");
    }
}
