package uk.gov.hmcts.reform.preapi.config;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.mediaservices.MediaServicesManager;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureConfiguration {
    @Bean
    public AzureProfile azureProfile(
        @Value("${azure.tenant-id}") String tenantId,
        @Value("${azure.subscription-id}") String subscriptionId
    ) {
        return new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
    }

    @Bean
    public TokenCredential tokenCredential(
        @Value("${azure.clientId}") String clientId,
        @Value("${azure.clientSecret}") String clientSecret,
        @Value("${azure.tenant-id}") String tenantId,
        AzureProfile profile
    ) {
        return new ClientSecretCredentialBuilder()
            .clientId(clientId)
            .clientSecret(clientSecret)
            .tenantId(tenantId)
            .authorityHost(profile.getEnvironment().getActiveDirectoryEndpoint())
            .build();
    }

    @Bean
    public AccessToken azureAccessToken(TokenCredential credential) {
        return credential
            .getToken(new TokenRequestContext().addScopes("https://management.azure.com/.default"))
            .block();
    }

    @Bean
    public AzureMediaServices amsClient(TokenCredential credentials, AzureProfile profile) {
        return MediaServicesManager.authenticate(credentials, profile).serviceClient();
    }
}
