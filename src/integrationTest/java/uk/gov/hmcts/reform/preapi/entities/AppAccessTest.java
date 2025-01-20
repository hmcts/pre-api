package uk.gov.hmcts.reform.preapi.entities;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class AppAccessTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveAppAccess() { //NOPMD - suppressed JUnit5TestShouldBePackagePrivate
        User user = HelperFactory.createUser(
            "Test",
            "User",
            "example@example.com",
            new Timestamp(System.currentTimeMillis()),
            null,
            null
        );
        entityManager.persist(user);

        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "Test123");
        entityManager.persist(court);

        Role role = HelperFactory.createRole("TestRole");
        entityManager.persist(role);

        AppAccess appAccess = HelperFactory.createAppAccess(
            user,
            court,
            role,
            true,
            new Timestamp(System.currentTimeMillis()),
            new Timestamp(System.currentTimeMillis()),
            true
        );

        entityManager.persist(appAccess);
        entityManager.flush();

        AppAccess retrievedAppAccess = entityManager.find(AppAccess.class, appAccess.getId());

        assertEquals(appAccess.getId(), retrievedAppAccess.getId(), "Ids should match");
        assertEquals(appAccess.getUser(), retrievedAppAccess.getUser(), "Users should match");
        assertEquals(appAccess.getCourt(), retrievedAppAccess.getCourt(), "Courts should match");
        assertEquals(appAccess.getRole(), retrievedAppAccess.getRole(), "Roles should match");
        assertEquals(appAccess.getLastAccess(), retrievedAppAccess.getLastAccess(), "Last access should match");
        assertEquals(appAccess.isActive(), retrievedAppAccess.isActive(), "Active status should match");
        assertEquals(appAccess.getDeletedAt(), retrievedAppAccess.getDeletedAt(), "Deleted at should match");
        assertEquals(appAccess.getCreatedAt(), retrievedAppAccess.getCreatedAt(), "Created at should match");
        assertEquals(appAccess.getModifiedAt(), retrievedAppAccess.getModifiedAt(), "Modified at should match");
    }
}

