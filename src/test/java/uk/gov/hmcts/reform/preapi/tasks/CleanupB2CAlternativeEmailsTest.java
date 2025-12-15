package uk.gov.hmcts.reform.preapi.tasks;

import com.microsoft.graph.models.ObjectIdentity;
import com.microsoft.graph.models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.B2CGraphService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CleanupB2CAlternativeEmailsTest {

    @Mock
    private UserService userService;

    @Mock
    private UserAuthenticationService userAuthenticationService;

    @Mock
    private B2CGraphService b2cGraphService;

    @Test
    void run_skips_users_with_null_alternative_email_and_does_not_update_local_user() {
        // Prepare a single UserDTO with null alternativeEmail
        var user = new UserDTO();
        user.setId(UUID.randomUUID());
        user.setEmail("primary@cjsm.net");
        user.setAlternativeEmail(null);

        // stub repository paging to return one page with the single user
        when(userService.findPortalUsersWithCjsmEmail(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user)));

        // create task but override signInRobotUser to skip authentication
        var task = new CleanupB2CAlternativeEmails(
            userService, userAuthenticationService,
            "cron@local", b2cGraphService
        ) {
            @Override
            protected void signInRobotUser() {
                // no-op for tests
            }
        };

        // run the task
        task.run();

        // Because the user's alternativeEmail is null, the task should not call updateAlternativeEmail
        verify(userService, never()).updateAlternativeEmail(any(), any());
    }

    @Test
    void run_skips_when_b2c_user_not_found() {
        var user = new UserDTO();
        user.setId(UUID.randomUUID());
        user.setEmail("primary@cjsm.net");
        user.setAlternativeEmail("alt@cjsm.net");

        when(userService.findPortalUsersWithCjsmEmail(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user)));

        when(b2cGraphService.findUserByPrimaryEmail(user.getEmail()))
            .thenReturn(Optional.empty());

        var task = new CleanupB2CAlternativeEmails(
            userService, userAuthenticationService,
            "cron@local", b2cGraphService
        ) {
            @Override
            protected void signInRobotUser() {
            }
        };

        task.run();

        // Ensure we didn't attempt to update identities or the local user
        verify(b2cGraphService, never()).updateUserIdentities(any(), anyList());
        verify(userService, never()).updateAlternativeEmail(any(), any());
    }

    @Test
    void run_skips_when_b2c_user_has_no_identities() {
        var user = new UserDTO();
        user.setId(UUID.randomUUID());
        user.setEmail("primary@cjsm.net");
        user.setAlternativeEmail("alt@cjsm.net");

        when(userService.findPortalUsersWithCjsmEmail(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user)));

        User gUser = new User();
        gUser.setId("b2c-id");
        gUser.setIdentities(null);

        when(b2cGraphService.findUserByPrimaryEmail(user.getEmail()))
            .thenReturn(Optional.of(gUser));

        var task = new CleanupB2CAlternativeEmails(
            userService, userAuthenticationService,
            "cron@local", b2cGraphService
        ) {
            @Override
            protected void signInRobotUser() {
            }
        };

        task.run();

        verify(b2cGraphService, never()).updateUserIdentities(any(), anyList());
        verify(userService, never()).updateAlternativeEmail(any(), any());
    }

    @Test
    void run_removes_alternative_identity_and_updates_local_user() {
        var user = new UserDTO();
        user.setId(UUID.randomUUID());
        user.setEmail("primary@cjsm.net");
        user.setAlternativeEmail("alt@cjsm.net");

        when(userService.findPortalUsersWithCjsmEmail(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user)));

        User gUser = new User();
        gUser.setId("b2c-id");

        var identities = new ArrayList<ObjectIdentity>();
        var keep = new ObjectIdentity();
        keep.setIssuer("issuer");
        keep.setIssuerAssignedId("primary@cjsm.net");
        identities.add(keep);

        var remove = new ObjectIdentity();
        remove.setIssuer("issuer");
        remove.setIssuerAssignedId("alt@cjsm.net");
        identities.add(remove);

        gUser.setIdentities(identities);

        when(b2cGraphService.findUserByPrimaryEmail(user.getEmail()))
            .thenReturn(Optional.of(gUser));

        var task = new CleanupB2CAlternativeEmails(
            userService, userAuthenticationService,
            "cron@local", b2cGraphService
        ) {
            @Override
            protected void signInRobotUser() {
            }
        };

        task.run();

        // Graph update called to patch identities
        verify(b2cGraphService, times(1)).updateUserIdentities(eq(gUser.getId()), anyList());
        // Local user updated to remove alternative email
        verify(userService, times(1)).updateAlternativeEmail(eq(user.getId()), eq(null));
    }

    @Test
    void run_processes_multiple_pages() {
        var user1 = new UserDTO();
        user1.setId(UUID.randomUUID());
        user1.setEmail("p1@cjsm.net");
        user1.setAlternativeEmail("alt1@cjsm.net");

        var user2 = new UserDTO();
        user2.setId(UUID.randomUUID());
        user2.setEmail("p2@cjsm.net");
        user2.setAlternativeEmail("alt2@cjsm.net");

        // Prepare corresponding graph users
        User g1 = new User();
        g1.setId("g1");
        var ids1 = new ArrayList<ObjectIdentity>();
        var keep1 = new ObjectIdentity();
        keep1.setIssuerAssignedId(user1.getEmail());
        ids1.add(keep1);
        var rem1 = new ObjectIdentity();
        rem1.setIssuerAssignedId(user1.getAlternativeEmail());
        ids1.add(rem1);
        g1.setIdentities(ids1);

        User g2 = new User();
        g2.setId("g2");
        var ids2 = new ArrayList<ObjectIdentity>();
        var keep2 = new ObjectIdentity();
        keep2.setIssuerAssignedId(user2.getEmail());
        ids2.add(keep2);
        var rem2 = new ObjectIdentity();
        rem2.setIssuerAssignedId(user2.getAlternativeEmail());
        ids2.add(rem2);
        g2.setIdentities(ids2);

        // Stub repository to return two pages sequentially (page0 then page1)
        var page0 = new PageImpl<>(List.of(user1), Pageable.ofSize(50).withPage(0), 51);
        var page1 = new PageImpl<>(List.of(user2), Pageable.ofSize(50).withPage(1), 51);

        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        when(userService.findPortalUsersWithCjsmEmail(any(Pageable.class))).thenAnswer(invocation -> {
            return callCount.getAndIncrement() == 0 ? page0 : page1;
        });

        when(b2cGraphService.findUserByPrimaryEmail(user1.getEmail())).thenReturn(Optional.of(g1));
        when(b2cGraphService.findUserByPrimaryEmail(user2.getEmail())).thenReturn(Optional.of(g2));

        var task = new CleanupB2CAlternativeEmails(
            userService, userAuthenticationService,
            "cron@local", b2cGraphService
        ) {
            @Override
            protected void signInRobotUser() {
            }
        };

        task.run();

        // Both graph updates should be invoked and both local users updated
        verify(b2cGraphService, times(1)).updateUserIdentities(eq(g1.getId()), anyList());
        verify(b2cGraphService, times(1)).updateUserIdentities(eq(g2.getId()), anyList());
        verify(userService, times(1)).updateAlternativeEmail(eq(user1.getId()), eq(null));
        verify(userService, times(1)).updateAlternativeEmail(eq(user2.getId()), eq(null));
    }

    @Test
    void run_handles_graph_update_exception_and_does_not_update_local_user() {
        var user = new UserDTO();
        user.setId(UUID.randomUUID());
        user.setEmail("primary@cjsm.net");
        user.setAlternativeEmail("alt@cjsm.net");

        when(userService.findPortalUsersWithCjsmEmail(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(user)));

        User gUser = new User();
        gUser.setId("b2c-id");
        var identities = new ArrayList<ObjectIdentity>();
        var keep = new ObjectIdentity();
        keep.setIssuerAssignedId(user.getEmail());
        identities.add(keep);
        var rem = new ObjectIdentity();
        rem.setIssuerAssignedId(user.getAlternativeEmail());
        identities.add(rem);
        gUser.setIdentities(identities);

        when(b2cGraphService.findUserByPrimaryEmail(user.getEmail())).thenReturn(Optional.of(gUser));

        // Simulate graph client throwing when updating identities
        org.mockito.Mockito.doThrow(new RuntimeException("graph patch failed"))
            .when(b2cGraphService).updateUserIdentities(eq(gUser.getId()), anyList());

        var task = new CleanupB2CAlternativeEmails(
            userService, userAuthenticationService,
            "cron@local", b2cGraphService
        ) {
            @Override
            protected void signInRobotUser() {
            }
        };

        // Should not throw despite graph failure (task handles/logs it)
        task.run();

        // Graph update was attempted
        verify(b2cGraphService, times(1)).updateUserIdentities(eq(gUser.getId()), anyList());
        // Local update should NOT be called because graph update failed
        verify(userService, never()).updateAlternativeEmail(any(), any());
    }
}
