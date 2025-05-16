package uk.gov.hmcts.reform.preapi.config;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.identity.DefaultAzureCredential;
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

    @Value("${azure.finalStorage.accountName}")
    private String finalStorageAccountName;

    @Value("${azure.ingestStorage.accountName}")
    private String ingestStorageAccountName;

    @Value("${azure.managedIdentityClientId}")
    private String managedIdentityClientId;

    @Bean
    public BlobServiceClient ingestStorageClient() {

        if (!managedIdentityClientId.isEmpty()) {
            log.info("Using managed identity to authenticate with ingest storage account with clientId: {}",
                     managedIdentityClientId);
            return getBlobServiceClientUsingManagedIdentity(ingestStorageAccountName);
        }
        log.info("Using connection string to authenticate with ingest storage account");
        return getBlobServiceClientUsingConnectionString(ingestConnectionString, ingestStorageAccountName);
    }

    @Bean
    public BlobServiceClient finalStorageClient() {
        if (!managedIdentityClientId.isEmpty()) {
            log.info("Using managed identity to authenticate with final storage account with clientId: {}",
                     managedIdentityClientId);
            return getBlobServiceClientUsingManagedIdentity(finalStorageAccountName);
        }
        log.info("Using connection string to authenticate with final storage account");
        return getBlobServiceClientUsingConnectionString(finalConnectionString, finalStorageAccountName);
    }

    @Nullable
    private BlobServiceClient getBlobServiceClientUsingConnectionString(String connectionString,
                                                                        String storageAccountName) {
        try {
            String accountKey = Arrays.stream(connectionString.split(";"))
                .filter(s -> s.startsWith("AccountKey="))
                .findFirst()
                .orElse("")
                .replace("AccountKey=", "");
            StorageSharedKeyCredential credential = new StorageSharedKeyCredential(storageAccountName, accountKey);
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
            DefaultAzureCredential credential = new DefaultAzureCredentialBuilder()
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
}
