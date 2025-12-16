package uk.gov.hmcts.reform.preapi.utils;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = Application.class)
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class IntegrationTestBase {

    @MockitoBean
    protected EmailServiceFactory emailServiceFactory;

    @MockitoBean
    protected ShareBookingService shareBookingService;

    @Autowired
    protected EntityManager entityManager;

    protected static UserAuthentication mockAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.getCourtId()).thenReturn(UUID.randomUUID());
        when(mockAuth.hasRole("ROLE_SUPER_USER")).thenReturn(true);
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
        try {
            entityManager.clear();
            entityManager.flush();
        } catch (Exception ignored) {
            // ignored
        }
    }
}

