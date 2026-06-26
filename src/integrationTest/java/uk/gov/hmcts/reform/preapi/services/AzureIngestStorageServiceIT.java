package uk.gov.hmcts.reform.preapi.services;

import com.azure.storage.blob.BlobContainerClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.utils.AzuriteHelperUtil;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AzureIngestStorageServiceIT extends IntegrationTestBase {

    @Autowired
    private AzureIngestStorageService azureIngestStorageService;

    public BlobContainerClient testContainer;

    @DynamicPropertySource
    static void overrideAzureEndpoint(DynamicPropertyRegistry registry) {
        String endpoint = String.format(
            "http://%s:%d/devstoreaccount1",
            AzuriteHelperUtil.DOCKER_COMPOSE_CONTAINER.getHost(),
            AzuriteHelperUtil.DOCKER_COMPOSE_CONTAINER.getMappedPort(AzuriteHelperUtil.CONTAINER_PORT)
        );
        registry.add("azure.blob.endpointFormat", () -> endpoint);
    }

    public static void stopDocker() {
        AzuriteHelperUtil.DOCKER_COMPOSE_CONTAINER.stop();
    }

    public void createContainer() {
        testContainer = AzuriteHelperUtil.BLOB_SERVICE_CLIENT.getBlobContainerClient(AzuriteHelperUtil.CONTAINER_NAME);
        testContainer.create();
    }

    public void deleteContainer() {
        testContainer.delete();
    }

    @BeforeAll
    public static void setup() {
        AzuriteHelperUtil.initialize();
    }

    @AfterAll
    public static void teardown() {
        stopDocker();
    }

    @Test
    void shouldReturnFalseWhenCheckingForSectionFileAndSectionFileIsMissing() {
        createContainer();
        testContainer.getBlobClient("0/Notsection").upload(
            new ByteArrayInputStream("I'm not a section file".getBytes()),
            "I'm not a section file".length());
        boolean sectionFileExists = azureIngestStorageService.sectionFileExist(AzuriteHelperUtil.CONTAINER_NAME);
        assertFalse(sectionFileExists);
        deleteContainer();
    }

    @Test
    void shouldReturnFalseWhenCheckingForSectionFileAndContainerDoesNotExist() {
        boolean sectionFileExists = azureIngestStorageService.sectionFileExist(AzuriteHelperUtil.CONTAINER_NAME);
        assertFalse(sectionFileExists);
    }

    @Test
    void shouldReturnTrueWhenSectionFileExists() {
        createContainer();
        testContainer.getBlobClient("0/section").upload(
            new ByteArrayInputStream("section file content".getBytes()),
            "section file content".length()
        );
        boolean sectionFileExists = azureIngestStorageService.sectionFileExist(AzuriteHelperUtil.CONTAINER_NAME);
        assertTrue(sectionFileExists);
        deleteContainer();
    }
}
