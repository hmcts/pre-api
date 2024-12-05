package uk.gov.hmcts.reform.preapi.entities;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
@SuppressWarnings("PMD - JUnit5TestShouldBePackagePrivate")
class RoleTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveRole() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        Role testRole = new Role();
        testRole.setName("TestRoleName");
        entityManager.persist(testRole);
        entityManager.flush();

        Role retrievedRole = entityManager.find(Role.class, testRole.getId());

        assertEquals(testRole.getId(), retrievedRole.getId(), "Id should match");
        assertEquals(testRole.getName(), retrievedRole.getName(), "Name should match");
    }
}
