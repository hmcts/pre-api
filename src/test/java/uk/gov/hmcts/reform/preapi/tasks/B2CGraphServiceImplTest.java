package uk.gov.hmcts.reform.preapi.tasks;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

        // Configure the deep-stubbed graph client to throw when users().get(...) is invoked
        when(graphServiceClient.users().get(any())).thenThrow(new RuntimeException("graph failure"));

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
}
