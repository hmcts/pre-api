package uk.gov.hmcts.reform.preapi.services;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.models.ObjectIdentity;
import com.microsoft.graph.models.User;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class B2CGraphServiceImpl implements B2CGraphService {

    private final GraphServiceClient graphClient;
    private final String tenantId;

    @Autowired
    public B2CGraphServiceImpl(
        @Value("${azure.b2c.clientId:}") String clientId,
        @Value("${azure.b2c.clientSecret:}") String clientSecret,
        @Value("${azure.b2c.tenantId:}") String tenantId
    ) {
        this.tenantId = tenantId != null ? tenantId : "";
        this.graphClient = initializeGraphClient(clientId, clientSecret, this.tenantId);
    }

    // Constructor for testing
    public B2CGraphServiceImpl(GraphServiceClient graphClient, String tenantId) {
        this.graphClient = graphClient;
        this.tenantId = tenantId != null ? tenantId : "";
    }

    private GraphServiceClient initializeGraphClient(String clientId, String clientSecret, String tenantId) {
        if (clientId != null && !clientId.isEmpty()
            && clientSecret != null && !clientSecret.isEmpty()
            && !tenantId.isEmpty()) {
            try {
                ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .tenantId(tenantId)
                    .build();

                log.info("GraphServiceClient initialized successfully");
                return new GraphServiceClient(credential);
            } catch (Exception e) {
                log.error("Failed to initialize GraphServiceClient: {}", e.getMessage(), e);
                return null;
            }
        } else {
            log.warn("Azure B2C credentials not configured, GraphServiceClient will not be available");
            return null;
        }
    }

    @Override
    public Optional<User> findUserByPrimaryEmail(String primaryEmail) {
        if (graphClient == null) {
            log.warn("GraphServiceClient is not configured; cannot query B2C for {}", primaryEmail);
            return Optional.empty();
        }

        try {
            UserCollectionResponse users = graphClient.users()
                .get(requestConfiguration -> {
                    if (requestConfiguration.queryParameters != null) {
                        requestConfiguration.queryParameters.filter =
                            String.format(
                                "identities/any(c:c/issuerAssignedId eq '%s' and c/issuer eq '%s.onmicrosoft.com')",
                                primaryEmail, tenantId
                            );
                        requestConfiguration.queryParameters.select = new String[]{"id", "identities"};
                    }
                });

            if (users == null || users.getValue() == null || users.getValue().isEmpty()) {
                return Optional.empty();
            }

            int limitOfUsersWithSameEmail = 1;
            if (users.getValue().size() > limitOfUsersWithSameEmail) {
                log.error("Multiple B2C users found with email: {}", primaryEmail);
                return Optional.empty();
            }

            return Optional.of(users.getValue().getFirst());
        } catch (Exception e) {
            log.error("Graph findUserByPrimaryEmail failed for {}: {}", primaryEmail, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public void updateUserIdentities(String userId, List<ObjectIdentity> identities) {
        if (graphClient == null) {
            throw new IllegalStateException("GraphServiceClient is not configured");
        }

        try {
            User userUpdate = new User();
            userUpdate.setIdentities(identities);
            graphClient.users().byUserId(userId).patch(userUpdate);
        } catch (Exception e) {
            log.error("Graph updateUserIdentities failed for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}
