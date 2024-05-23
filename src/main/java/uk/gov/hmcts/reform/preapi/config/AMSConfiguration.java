package uk.gov.hmcts.reform.preapi.config;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.mediaservices.MediaServicesManager;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AMSConfiguration {
    @Bean
    public AzureMediaServices amsClient(
        @Value("${azure.tenant-id}") String tenantId,
        @Value("${azure.subscription-id}") String subscriptionId,
        @Value("${azure.clientId}") String clientId,
        @Value("${azure.clientSecret}") String clientSecret
    ) {
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
        var credentials = new ClientSecretCredentialBuilder()
            .clientId(clientId)
            .clientSecret(clientSecret)
            .tenantId(tenantId)
            .authorityHost(profile.getEnvironment().getActiveDirectoryEndpoint())
            .build();
        return MediaServicesManager.authenticate(credentials, profile).serviceClient();
    }
}
