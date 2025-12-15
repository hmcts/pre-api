package uk.gov.hmcts.reform.preapi.services;

import com.microsoft.graph.models.ObjectIdentity;
import com.microsoft.graph.models.User;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class B2CGraphServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private GraphServiceClient graphServiceClient;

    @InjectMocks
    private B2CGraphServiceImpl b2cGraphService;

    @Test
    void findUserByPrimaryEmail_returns_empty_on_graph_exception() {
        var tenantId = "tenant";
        b2cGraphService = new B2CGraphServiceImpl(graphServiceClient, tenantId);

        when(graphServiceClient.users().get(any())).thenThrow(new RuntimeException("graph failure"));

        Optional<User> result = b2cGraphService.findUserByPrimaryEmail("test@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void findUserByPrimaryEmail_returns_user_when_single_result() {
        var tenantId = "tenant";
        b2cGraphService = new B2CGraphServiceImpl(graphServiceClient, tenantId);

        User gUser = new User();
        gUser.setId("user-id-1");

        // deep-stub: when the SDK call .users().get(...) is invoked, its returned mock's getValue() should return our list
        when(graphServiceClient.users().get(any()).getValue()).thenReturn(List.of(gUser));

        Optional<User> result = b2cGraphService.findUserByPrimaryEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("user-id-1");
    }

    @Test
    void findUserByPrimaryEmail_returns_empty_when_multiple_users_found() {
        var tenantId = "tenant";
        b2cGraphService = new B2CGraphServiceImpl(graphServiceClient, tenantId);

        User g1 = new User(); g1.setId("u1");
        User g2 = new User(); g2.setId("u2");

        when(graphServiceClient.users().get(any()).getValue()).thenReturn(List.of(g1, g2));

        Optional<User> result = b2cGraphService.findUserByPrimaryEmail("test@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void updateUserIdentities_calls_patch_on_graph_client() {
        var tenantId = "tenant";
        b2cGraphService = new B2CGraphServiceImpl(graphServiceClient, tenantId);

        var identities = new ArrayList<ObjectIdentity>();

        b2cGraphService.updateUserIdentities("some-user-id", identities);

        verify(graphServiceClient.users().byUserId("some-user-id")).patch(any(User.class));
    }

    @Test
    void updateUserIdentities_propagates_graph_exception() {
        var tenantId = "tenant";
        b2cGraphService = new B2CGraphServiceImpl(graphServiceClient, tenantId);

        // configure the deep-stubbed chain to throw when byUserId(...) is invoked (so patch will fail)
        when(graphServiceClient.users().byUserId(anyString())).thenThrow(new RuntimeException("patch failed"));

        var identities = new ArrayList<ObjectIdentity>();

        assertThatThrownBy(() -> b2cGraphService.updateUserIdentities("some-user-id", identities))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("patch failed");

        // verify patch was attempted (byUserId was called)
        // can't verify .patch because byUserId threw; verify byUserId invocation instead
        verify(graphServiceClient.users()).byUserId("some-user-id");
    }

    @Test
    void updateUserIdentities_throws_when_client_not_configured() {
        // create service with no graph client configured (no-arg constructor)
        var svc = new B2CGraphServiceImpl();

        var identities = new ArrayList<ObjectIdentity>();

        assertThatThrownBy(() -> svc.updateUserIdentities("id", identities))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GraphServiceClient is not configured");
    }
}
