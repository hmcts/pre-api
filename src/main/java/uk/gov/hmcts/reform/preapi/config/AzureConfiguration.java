package uk.gov.hmcts.reform.preapi.config;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.mediaservices.MediaServicesManager;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import javax.annotation.Nullable;


@Configuration
public class AzureConfiguration {
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

    @Bean
    public BlobServiceClient ingestStorageClient(
        @Value("${azure.ingestStorage.connectionString}") String connectionString,
        @Value("${azure.ingestStorage.accountName}") String ingestStorageAccountName
    ) {
        return getBlobServiceClient(connectionString, ingestStorageAccountName);
    }

    @Bean
    public BlobServiceClient finalStorageClient(
        @Value("${azure.finalStorage.connectionString}") String connectionString,
        @Value("${azure.finalStorage.accountName}") String finalStorageAccountName
    ) {
        return getBlobServiceClient(connectionString, finalStorageAccountName);
    }

    @Nullable
    private BlobServiceClient getBlobServiceClient(String connectionString, String storageAccountName) {
        try {
            var accountKey = Arrays.stream(connectionString.split(";"))
                .filter(s -> s.startsWith("AccountKey="))
                .findFirst()
                .orElse("")
                .replace("AccountKey=", "");
            var credential = new StorageSharedKeyCredential(storageAccountName, accountKey);
            return new BlobServiceClientBuilder()
                .credential(credential)
                .endpoint(String.format("https://%s.blob.core.windows.net", storageAccountName))
                .buildClient();
        } catch (Exception e) {
            return null;
        }
    }
}
