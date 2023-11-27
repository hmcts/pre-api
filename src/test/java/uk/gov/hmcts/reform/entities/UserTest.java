package uk.gov.hmcts.reform.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.User;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class UserTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveUser() {
        User user = HelperFactory.createUser("Test", "User", "example@example.com", false, null, null);
        entityManager.persist(user);
        entityManager.flush();

        User retrievedUser = entityManager.find(User.class, user.getId());

        assertEquals(user.getId(), retrievedUser.getId(), "Id should match");
        assertEquals(user.getFirstName(), retrievedUser.getFirstName(), "First name should match");
        assertEquals(user.getLastName(), retrievedUser.getLastName(), "Last name should match");
        assertEquals(user.getEmail(), retrievedUser.getEmail(), "Email should match");
        assertEquals(user.getOrganisation(), retrievedUser.getOrganisation(), "Organisation should match");
        assertEquals(user.getPhone(), retrievedUser.getPhone(), "Phone should match");
        assertEquals(user.isDeleted(), retrievedUser.isDeleted(), "Deleted status should match");
        assertEquals(user.getCreatedAt(), retrievedUser.getCreatedAt(), "Created at should match");
        assertEquals(user.getModifiedAt(), retrievedUser.getModifiedAt(), "Modified at should match");
    }
}
