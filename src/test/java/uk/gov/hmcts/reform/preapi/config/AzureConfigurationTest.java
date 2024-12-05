package uk.gov.hmcts.reform.preapi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AzureConfiguration.class)
@TestPropertySource(properties = {
    "azure.managedIdentityClientId=managedIdentityClientId"
})
public class AzureConfigurationTest {
    
    @Autowired
    AzureConfiguration azureConfiguration;

    @DisplayName("Test Jackson config for timestamp mapping")
    @Test
    void testGetBlobServiceClientUsingManagedIdentity() {

        var client = azureConfiguration.ingestStorageClient();
        assertThat(client).isNotNull();

        var client2 = azureConfiguration.finalStorageClient();
        assertThat(client2).isNotNull();
    }
}
