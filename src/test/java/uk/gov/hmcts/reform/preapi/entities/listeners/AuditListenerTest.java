package uk.gov.hmcts.reform.preapi.entities.listeners;

import jakarta.persistence.Table;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.AuditAction;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AuditListener.class)
public class AuditListenerTest {
    @MockBean
    private UserAuthentication userAuthentication;

    @MockBean
    private AppAccessRepository appAccessRepository;

    @MockBean
    private AuditRepository auditRepository;

    @MockBean
    private HttpServletRequest request;

    @Autowired
    private AuditListener auditListener;

    @Test
    @DisplayName("Should get user id from context when app user")
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
    @DisplayName("Should set userid to null when app user and app access is null")
    void getUserIdFromContextAppUserNull() {
        when(userAuthentication.isAppUser()).thenReturn(true);
        when(userAuthentication.getAppAccess()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(userAuthentication);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isNull();
    }

    @Test
    @DisplayName("Should get user id from context when portal user")
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
    @DisplayName("Should set userid to null when portal user and portal access is null")
    void getUserIdFromContextPortalUserNull() {
        when(userAuthentication.isAppUser()).thenReturn(false);
        when(userAuthentication.getPortalAccess()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(userAuthentication);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isNull();
    }

    @Test
    @DisplayName("Should set userid to null when context authentication is null")
    void getUserIdFromContextNullAuth() {
        SecurityContextHolder.getContext().setAuthentication(null);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isNull();
    }

    @Test
    @DisplayName("Should set userid to null when context authentication is not UserAuthentication")
    void getUserIdFromContextAnonAuth() {
        var anonAuth = mock(AnonymousAuthenticationToken.class);
        SecurityContextHolder.getContext().setAuthentication(anonAuth);

        var userId = auditListener.getUserIdFromContext();

        assertThat(userId).isNull();
    }

    @Test
    @DisplayName("Should create CREATE audit on entity creation")
    void shouldAuditOnPrePersist() {
        auditListener.prePersist(new MockEntity());

        var argumentCaptor = ArgumentCaptor.forClass(Audit.class);
        verify(auditRepository, times(1)).save(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().getActivity()).isEqualTo(AuditAction.CREATE.toString());
    }

    @Test
    @DisplayName("Should create UPDATE audit on entity update")
    void shouldAuditOnPreUpdate() {
        auditListener.preUpdate(new MockEntity());

        var argumentCaptor = ArgumentCaptor.forClass(Audit.class);
        verify(auditRepository, times(1)).save(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().getActivity()).isEqualTo(AuditAction.UPDATE.toString());
    }

    @Test
    @DisplayName("Should create DELETE audit on entity deletion")
    void shouldAuditOnPreRemove() {
        auditListener.preRemove(new MockEntity());

        var argumentCaptor = ArgumentCaptor.forClass(Audit.class);
        verify(auditRepository, times(1)).save(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().getActivity()).isEqualTo(AuditAction.DELETE.toString());
    }

    @Test
    @DisplayName("Should not audit when disabled for class and action")
    void shouldNotAuditWhenDisabledForClassAndAction() {
        AuditListener.disableAuditingForClass(MockEntity.class, Set.of(AuditAction.CREATE));

        auditListener.prePersist(new MockEntity());

        verify(auditRepository, never()).save(any(Audit.class));
    }

    @Test
    @DisplayName("Should audit when audit has been re-enabled for class")
    void shouldAuditWhenEnabledForClass() {
        AuditListener.disableAuditingForClass(MockEntity.class, Set.of(AuditAction.CREATE));
        AuditListener.enableAuditingForClass(MockEntity.class);

        auditListener.prePersist(new MockEntity());

        verify(auditRepository, times(1)).save(any(Audit.class));
    }

    @Test
    @DisplayName("Should enable/disable audits correctly when in a multi-threaded environment")
    void shouldMaintainThreadLocalIsolation() throws InterruptedException {
        var thread1AuditEnabled = new AtomicBoolean(false);
        var thread2AuditEnabled = new AtomicBoolean(false);

        var entityClass = MockEntity.class;

        var thread1 = new Thread(() -> {
            AuditListener.disableAuditingForClass(entityClass, Set.of(AuditAction.CREATE));
            thread1AuditEnabled.set(AuditListener.isAuditableEntity(entityClass, AuditAction.CREATE));
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        var thread2 = new Thread(() ->
            thread2AuditEnabled.set(AuditListener.isAuditableEntity(entityClass, AuditAction.CREATE))
        );

        thread1.start();
        Thread.sleep(50);
        thread2.start();

        thread1.join();
        thread2.join();

        assertThat(thread1AuditEnabled.get()).isFalse();
        assertThat(thread2AuditEnabled.get()).isTrue();
    }

    @Table(name = "mock_table")
    private static class MockEntity extends BaseEntity {
    }
}
