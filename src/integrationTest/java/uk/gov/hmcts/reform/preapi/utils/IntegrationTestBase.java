package uk.gov.hmcts.reform.preapi.utils;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = Application.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class IntegrationTestBase {

    @Autowired
    protected EntityManager entityManager;

    protected static UserAuthentication mockAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
        return mockAuth;
    }

    protected static UserAuthentication mockNonAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.isPortalUser()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
        return mockAuth;
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();
        entityManager.flush();
    }
}
