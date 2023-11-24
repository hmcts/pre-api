package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;

@SpringBootTest(classes = Application.class)
public class PortalAccessTest {

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

        assertEquals(testPortalAccess.getId(), retrievedPortalAccess.getId());
        assertEquals(testPortalAccess.getUser(), retrievedPortalAccess.getUser());
        assertEquals(testPortalAccess.getPassword(), retrievedPortalAccess.getPassword());
        assertEquals(testPortalAccess.getLastAccess(), retrievedPortalAccess.getLastAccess());
        assertEquals(testPortalAccess.isInvitationSent(), retrievedPortalAccess.isInvitationSent());
        assertEquals(testPortalAccess.getInvitationDateTime(), retrievedPortalAccess.getInvitationDateTime());
        assertEquals(testPortalAccess.isRegistered(), retrievedPortalAccess.isRegistered());
        assertEquals(testPortalAccess.getRegisteredDateTime(), retrievedPortalAccess.getRegisteredDateTime());
        assertEquals(testPortalAccess.isActive(), retrievedPortalAccess.isActive());
        assertEquals(testPortalAccess.isDeleted(), retrievedPortalAccess.isDeleted());
        assertEquals(testPortalAccess.getCreatedOn(), retrievedPortalAccess.getCreatedOn());
        assertEquals(testPortalAccess.getModifiedOn(), retrievedPortalAccess.getModifiedOn());
    }
}
