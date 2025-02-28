package uk.gov.hmcts.reform.preapi.entities.listeners;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AuditListener.class)
public class AuditListenerTest {
    @MockitoBean
    private UserAuthentication userAuthentication;

    @MockitoBean
    private AppAccessRepository appAccessRepository;

    @MockitoBean
    private HttpServletRequest request;

    @Autowired
    private AuditListener auditListener;

    @Test
    void getUserIdFromContextAppUser() {
        var appAccess = new AppAccess();
        appAccess.setId(UUID.randomUUID());
        when(userAuthentication.isAppUser()).thenReturn(true);
        when(userAuthentication.getAppAccess()).thenReturn(appAccess);
        SecurityContextHolder.getContext().setAuthentication(userAuthentication);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isEqualTo(appAccess.getId());
    }

    @Test
    void getUserIdFromContextAppUserNull() {
        when(userAuthentication.isAppUser()).thenReturn(true);
        when(userAuthentication.getAppAccess()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(userAuthentication);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isNull();
    }

    @Test
    void getUserIdFromContextPortalUser() {
        var portalAccess = new PortalAccess();
        portalAccess.setId(UUID.randomUUID());
        when(userAuthentication.isAppUser()).thenReturn(false);
        when(userAuthentication.getPortalAccess()).thenReturn(portalAccess);
        SecurityContextHolder.getContext().setAuthentication(userAuthentication);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isEqualTo(portalAccess.getId());
    }

    @Test
    void getUserIdFromContextPortalUserNull() {
        when(userAuthentication.isAppUser()).thenReturn(false);
        when(userAuthentication.getPortalAccess()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(userAuthentication);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isNull();
    }

    @Test
    void getUserIdFromContextNullAuth() {
        SecurityContextHolder.getContext().setAuthentication(null);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isNull();
    }

    @Test
    void getUserIdFromContextAnonAuth() {
        var anonAuth = mock(AnonymousAuthenticationToken.class);
        SecurityContextHolder.getContext().setAuthentication(anonAuth);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isNull();
    }
}
