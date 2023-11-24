package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.User;

@SpringBootTest(classes = Application.class)
public class UserTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveUser() {
        User user = HelperFactory.createUser("Test", "User", "example@example.com", false, null, null);
        entityManager.persist(user);
        entityManager.flush();

        User retrievedUser = entityManager.find(User.class, user.getId());

        assertEquals(user.getId(), retrievedUser.getId());
        assertEquals(user.getFirstName(), retrievedUser.getFirstName());
        assertEquals(user.getLastName(), retrievedUser.getLastName());
        assertEquals(user.getEmail(), retrievedUser.getEmail());
        assertEquals(user.getOrganisation(), retrievedUser.getOrganisation());
        assertEquals(user.getPhone(), retrievedUser.getPhone());
        assertEquals(user.isDeleted(), retrievedUser.isDeleted());
        assertEquals(user.getCreatedOn(), retrievedUser.getCreatedOn());
        assertEquals(user.getModifiedOn(), retrievedUser.getModifiedOn());
    }
}
