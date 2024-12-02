package uk.gov.hmcts.reform.preapi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AzureConfigurationTest {

    @DisplayName("Test Jackson config for timestamp mapping")
    @Test
    void testGetBlobServiceClientUsingManagedIdentity() {
        var  azureConfiguration = new AzureConfiguration();

        var client = azureConfiguration.ingestStorageClient("connectionString",
                                                            "ingestStorageAccountName",
                                                            "managedIdentityClientId");
        assertThat(client).isNotNull();

        var client2 = azureConfiguration.finalStorageClient("connectionString",
                                                            "ingestStorageAccountName",
                                                            "managedIdentityClientId");
        assertThat(client).isNotNull();
    }
}
