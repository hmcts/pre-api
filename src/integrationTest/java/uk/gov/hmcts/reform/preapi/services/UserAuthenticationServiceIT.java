package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserAuthenticationServiceIT extends IntegrationTestBase {
    @Autowired
    private UserAuthenticationService userAuthenticationService;

    private AppAccess appAccess;
    private PortalAccess portalAccess;
    private Timestamp lastAccess;

    @BeforeEach
    void setUp() {
        lastAccess = Timestamp.from(Instant.now());
        var user = HelperFactory.createUser(
            "Example",
            "Example",
            "example@example.com",
            null,
            null,
            null
        );
        entityManager.persist(user);

        var role = HelperFactory.createRole("ROLE_USER");
        entityManager.persist(role);

        var court = HelperFactory.createCourt(
            CourtType.CROWN,
            "Example court",
            "1234567890"
        );
        entityManager.persist(court);

        appAccess = HelperFactory.createAppAccess(user, court, role, true, null, lastAccess, true);
        portalAccess = HelperFactory.createPortalAccess(
            user,
            lastAccess,
            AccessStatus.ACTIVE,
            Timestamp.from(Instant.now()),
            Timestamp.from(Instant.now()),
            null
        );
        entityManager.persist(appAccess);
        entityManager.persist(portalAccess);
    }

    @Transactional
    @Test
    public void loadAppUserByIdSuccess() {
        var userWithValidId = userAuthenticationService.loadAppUserById(appAccess.getId().toString());
        assertEquals(userWithValidId.getUserId(), appAccess.getUser().getId());
        assertEquals(userWithValidId.getAppAccess(), appAccess);

        entityManager.refresh(appAccess);
        assertFalse(lastAccess.equals(userWithValidId.getAppAccess().getLastAccess()));
        assertTrue(lastAccess.before(appAccess.getLastAccess()));
    }

    @Transactional
    @Test
    public void loadPortalUserByIdSuccess() {
        var userWithValidId = userAuthenticationService.loadAppUserById(portalAccess.getId().toString());
        assertEquals(userWithValidId.getUserId(), portalAccess.getUser().getId());
        assertEquals(userWithValidId.getPortalAccess(), portalAccess);

        entityManager.refresh(portalAccess);
        assertFalse(lastAccess.equals(userWithValidId.getPortalAccess().getLastAccess()));
        assertTrue(lastAccess.before(portalAccess.getLastAccess()));
    }

    @Transactional
    @Test
    public void loadUserByIdNotFound() {
        var id = UUID.randomUUID();
        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(id.toString())
        ).getMessage();

        assertEquals(message, "Unauthorised user: " + id);
    }

    @Transactional
    @Test
    public void loadUserByIdNull() {
        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(null)
        ).getMessage();

        assertEquals(message, "Unauthorised user: null");
    }


    @Transactional
    @Test
    public void loadUserByIdNotUuid() {
        var id = "1234567890";
        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(id)
        ).getMessage();

        assertEquals(message, "Unauthorised user: " + id);
    }

    @Transactional
    @Test
    public void loadUserByIdEmpty() {
        var id = "";
        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(id)
        ).getMessage();

        assertEquals(message, "Unauthorised user: " + id);
    }

    @Test
    @Transactional
    public void loadUserByIdAppInactive() {
        appAccess.setActive(false);
        entityManager.persist(appAccess);
        var id = appAccess.getId().toString();

        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(id)
        ).getMessage();

        assertEquals(message, "Unauthorised user: " + id);
    }

    @Test
    @Transactional
    public void loadUserByIdAppDeleted() {
        appAccess.setDeletedAt(Timestamp.from(Instant.now()));
        entityManager.persist(appAccess);
        var id = appAccess.getId().toString();

        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(id)
        ).getMessage();

        assertEquals(message, "Unauthorised user: " + id);
    }

    @Test
    @Transactional
    public void loadUserByIdAppUserDeleted() {
        var user = appAccess.getUser();
        user.setDeletedAt(Timestamp.from(Instant.now()));
        entityManager.persist(user);
        var id = appAccess.getId().toString();

        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(id)
        ).getMessage();

        assertEquals(message, "Unauthorised user: " + id);
    }
}
