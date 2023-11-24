package uk.gov.hmcts.reform.entities;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;

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

@SpringBootTest(classes = Application.class)
public class AppAccessTest {

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

        assertEquals(appAccess.getId(), retrievedAppAccess.getId());
        assertEquals(appAccess.getUser(), retrievedAppAccess.getUser());
        assertEquals(appAccess.getCourt(), retrievedAppAccess.getCourt());
        assertEquals(appAccess.getRole(), retrievedAppAccess.getRole());
        assertEquals(appAccess.getLastAccess(), retrievedAppAccess.getLastAccess());
        assertEquals(appAccess.isActive(), retrievedAppAccess.isActive());
        assertEquals(appAccess.isDeleted(), retrievedAppAccess.isDeleted());
    }
}

