package uk.gov.hmcts.reform.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class PortalAccessTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrievePortalAccess() {
        User user = HelperFactory.createUser("Test", "User", "example@example.com", false, null, null);
        entityManager.persist(user);

        PortalAccess testPortalAccess = new PortalAccess();
        testPortalAccess.setUser(user);
        testPortalAccess.setPassword("TestPassword");
        testPortalAccess.setLastAccess(new Timestamp(System.currentTimeMillis()));
        testPortalAccess.setInvitationSent(true);
        testPortalAccess.setInvitationDateTime(new Timestamp(System.currentTimeMillis()));
        testPortalAccess.setRegistered(true);
        testPortalAccess.setRegisteredDateTime(new Timestamp(System.currentTimeMillis()));
        testPortalAccess.setActive(true);
        testPortalAccess.setDeleted(false);

        entityManager.persist(testPortalAccess);
        entityManager.flush();

        PortalAccess retrievedPortalAccess = entityManager.find(PortalAccess.class, testPortalAccess.getId());

        assertEquals(testPortalAccess.getId(), retrievedPortalAccess.getId(), "Id should match");
        assertEquals(testPortalAccess.getUser(), retrievedPortalAccess.getUser(), "User should match");
        assertEquals(testPortalAccess.getPassword(), retrievedPortalAccess.getPassword(), "Password should match");
        assertEquals(
            testPortalAccess.getLastAccess(),
            retrievedPortalAccess.getLastAccess(),
            "Last active should match"
        );
        assertEquals(
            testPortalAccess.isInvitationSent(),
            retrievedPortalAccess.isInvitationSent(),
            "Invitation sent status should match"
        );
        assertEquals(
            testPortalAccess.getInvitationDateTime(),
            retrievedPortalAccess.getInvitationDateTime(),
            "Invitation date time should match"
        );
        assertEquals(
            testPortalAccess.isRegistered(),
            retrievedPortalAccess.isRegistered(),
            "Registered status should match"
        );
        assertEquals(
            testPortalAccess.getRegisteredDateTime(),
            retrievedPortalAccess.getRegisteredDateTime(),
            "Registered date time should match"
        );
        assertEquals(testPortalAccess.isActive(), retrievedPortalAccess.isActive(), "Active status should match");
        assertEquals(testPortalAccess.isDeleted(), retrievedPortalAccess.isDeleted(), "Deleted status should match");
        assertEquals(testPortalAccess.getCreatedOn(), retrievedPortalAccess.getCreatedOn(), "Created on should match");
        assertEquals(
            testPortalAccess.getModifiedOn(),
            retrievedPortalAccess.getModifiedOn(),
            "Modified on should match"
        );
    }
}
