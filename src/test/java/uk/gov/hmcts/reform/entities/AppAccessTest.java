package uk.gov.hmcts.reform.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

import java.sql.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = Application.class)
class AppAccessTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void testSaveAndRetrieveAppAccess() {
        User user = HelperFactory.createUser("Test", "User", "example@example.com", false, null, null);
        entityManager.persist(user);

        Court court = HelperFactory.createCourt(CourtType.crown, "Test Court", "Test123");
        entityManager.persist(court);

        Role role = HelperFactory.createRole("TestRole");
        entityManager.persist(role);

        AppAccess appAccess = HelperFactory.createAppAccess(
            user,
            court,
            role,
            true,
            false,
            new Date(System.currentTimeMillis())
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
        assertEquals(appAccess.isDeleted(), retrievedAppAccess.isDeleted(), "Deleted status should match");
    }
}

