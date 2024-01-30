package uk.gov.hmcts.reform.preapi.services;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.security.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest(classes = Application.class)
public class UserAuthenticationServiceIT {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserAuthenticationService userAuthenticationService;

    private AppAccess access;

    @BeforeEach
    void setUp() {
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

        access = HelperFactory.createAppAccess(user, court, role, true, null, null);
        entityManager.persist(access);
    }

    @Transactional
    @Test
    public void loadAppUserByIdSuccess() {
        var userWithValidId = userAuthenticationService.loadAppUserById(access.getUser().getId().toString());
        assertEquals(userWithValidId.getUserId(), access.getUser().getId());
        assertEquals(userWithValidId.getAppAccess(), List.of(access));
    }

    @Transactional
    @Test
    public void loadAppUserByIdNotFound() {
        var id = UUID.randomUUID();
        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(id.toString())
        ).getMessage();

        assertEquals(message, "Unauthorised user: " + id);
    }

    @Transactional
    @Test
    public void loadAppUserByIdNull() {
        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(null)
        ).getMessage();

        assertEquals(message, "Unauthorised user: null");
    }


    @Transactional
    @Test
    public void loadAppUserByIdNotUuid() {
        var id = "1234567890";
        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(id)
        ).getMessage();

        assertEquals(message, "Unauthorised user: " + id);
    }

    @Transactional
    @Test
    public void loadAppUserByIdEmpty() {
        var id = "";
        var message = assertThrows(
            BadCredentialsException.class,
            () ->  userAuthenticationService.loadAppUserById(id)
        ).getMessage();

        assertEquals(message, "Unauthorised user: " + id);
    }
}
