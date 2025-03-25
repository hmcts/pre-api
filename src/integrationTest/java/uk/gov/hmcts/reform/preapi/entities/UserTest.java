package uk.gov.hmcts.reform.preapi.entities;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class UserTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveUser() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        User user = HelperFactory.createDefaultTestUser();
        entityManager.persist(user);
        entityManager.flush();

        User retrievedUser = entityManager.find(User.class, user.getId());

        assertEquals(user.getId(), retrievedUser.getId(), "Id should match");
        assertEquals(user.getFirstName(), retrievedUser.getFirstName(), "First name should match");
        assertEquals(user.getLastName(), retrievedUser.getLastName(), "Last name should match");
        assertEquals(user.getEmail(), retrievedUser.getEmail(), "Email should match");
        assertEquals(user.getOrganisation(), retrievedUser.getOrganisation(), "Organisation should match");
        assertEquals(user.getPhone(), retrievedUser.getPhone(), "Phone should match");
        assertEquals(user.getDeletedAt(), retrievedUser.getDeletedAt(), "Deleted at should match");
        assertEquals(user.getCreatedAt(), retrievedUser.getCreatedAt(), "Created at should match");
        assertEquals(user.getModifiedAt(), retrievedUser.getModifiedAt(), "Modified at should match");
    }
}
