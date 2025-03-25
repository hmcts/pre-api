package uk.gov.hmcts.reform.preapi.entities;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class PortalAccessTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrievePortalAccess() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        User user = HelperFactory.createDefaultTestUser();
        entityManager.persist(user);

        PortalAccess testPortalAccess = new PortalAccess();
        testPortalAccess.setUser(user);
        testPortalAccess.setLastAccess(new Timestamp(System.currentTimeMillis()));
        testPortalAccess.setStatus(AccessStatus.REGISTERED);
        testPortalAccess.setInvitedAt(new Timestamp(System.currentTimeMillis()));
        testPortalAccess.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
        testPortalAccess.setDeletedAt(new Timestamp(System.currentTimeMillis()));

        entityManager.persist(testPortalAccess);
        entityManager.flush();

        PortalAccess retrievedPortalAccess = entityManager.find(PortalAccess.class, testPortalAccess.getId());

        assertEquals(testPortalAccess.getId(), retrievedPortalAccess.getId(), "Id should match");
        assertEquals(testPortalAccess.getUser(), retrievedPortalAccess.getUser(), "User should match");
        assertEquals(testPortalAccess.getStatus(), retrievedPortalAccess.getStatus(), "Status should match");
        assertEquals(
            testPortalAccess.getLastAccess(),
            retrievedPortalAccess.getLastAccess(),
            "Last active should match"
        );
        assertEquals(
            testPortalAccess.getInvitedAt(),
            retrievedPortalAccess.getInvitedAt(),
            "Invitation date time should match"
        );
        assertEquals(
            testPortalAccess.getRegisteredAt(),
            retrievedPortalAccess.getRegisteredAt(),
            "Registered date time should match"
        );
        assertEquals(testPortalAccess.getDeletedAt(), retrievedPortalAccess.getDeletedAt(), "Deleted at should match");
        assertEquals(testPortalAccess.getCreatedAt(), retrievedPortalAccess.getCreatedAt(), "Created at should match");
        assertEquals(
            testPortalAccess.getModifiedAt(),
            retrievedPortalAccess.getModifiedAt(),
            "Modified at should match"
        );
    }
}
