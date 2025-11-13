package uk.gov.hmcts.reform.preapi.config;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import javax.annotation.Nullable;

@Configuration
@Slf4j
public class AzureConfiguration {

    @Value("${azure.tenantId}")
    String tenantId;

    @Value("${azure.finalStorage.connectionString}")
    private String finalConnectionString;

    @Value("${azure.ingestStorage.connectionString}")
    private String ingestConnectionString;

    @Value("${azure.vodafoneStorage.connectionString}")
    private String vodafoneConnectionString;

    @Value("${azure.finalStorage.accountName}")
    private String finalStorageAccountName;

    @Value("${azure.ingestStorage.accountName}")
    private String ingestStorageAccountName;

    @Value("${azure.vodafoneStorage.accountName}")
    private String vodafoneStorageAccountName;

    @Value("${azure.managedIdentityClientId}")
    private String managedIdentityClientId;

    @Bean
    public BlobServiceClient ingestStorageClient() {
        return storageClient(ingestStorageAccountName, ingestConnectionString);
    }

    @Bean
    public BlobServiceClient finalStorageClient() {
        return storageClient(finalStorageAccountName, finalConnectionString);
    }

    @Bean
    public BlobServiceClient vodafoneStorageClient() {
        return storageClient(vodafoneStorageAccountName, vodafoneConnectionString);
    }

    private BlobServiceClient storageClient(String storageAccountName, String connectionString) {
        if (!managedIdentityClientId.isEmpty()) {
            log.info("Using managed identity to authenticate with {} account with clientId: {}",
                     storageAccountName, managedIdentityClientId);
            return getBlobServiceClientUsingManagedIdentity(storageAccountName);
        }
        log.info("Using connection string to authenticate with {} storage account", storageAccountName);
        return getBlobServiceClientUsingConnectionString(connectionString, storageAccountName);
    }

    @Nullable
    private BlobServiceClient getBlobServiceClientUsingConnectionString(String connectionString,
                                                                        String storageAccountName) {
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
                .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS))
                .buildClient();
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private BlobServiceClient getBlobServiceClientUsingManagedIdentity(String storageAccountName) {
        try {
            var credential = new DefaultAzureCredentialBuilder()
                .tenantId(tenantId)
                .managedIdentityClientId(managedIdentityClientId)
                .build();
            return new BlobServiceClientBuilder()
                .credential(credential)
                .endpoint(String.format("https://%s.blob.core.windows.net", storageAccountName))
                .httpLogOptions(new HttpLogOptions().setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS))
                .buildClient();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isUsingManagedIdentity() {
        return managedIdentityClientId != null && !managedIdentityClientId.isEmpty();
    }
}
