package uk.gov.hmcts.reform.preapi.utils;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.testcontainers.containers.GenericContainer;

public class AzuriteHelperUtil {

    public static final String AZURE_TEST_CONTAINER = "hmctspublic.azurecr.io/imported/azure-storage/azurite:3.29.0";
    public static final String EXTRACTION_HOST = "azurite";
    public static final int CONTAINER_PORT = 10000;
    public static final String CONTAINER_NAME = "61597863-1d08-4d5b-9c30-845f7a8eabfd";
    public static final GenericContainer<?> DOCKER_COMPOSE_CONTAINER =
        new GenericContainer<>(AZURE_TEST_CONTAINER).withExposedPorts(CONTAINER_PORT)
            .withCommand("azurite-blob --blobHost 0.0.0.0 --blobPort 10000 --skipApiVersionCheck");

    public static final String STORAGE_CONN_STRING = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
        + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
        + "BlobEndpoint=http://%s:%d/devstoreaccount1;";
    public static BlobServiceClient BLOB_SERVICE_CLIENT;

    private AzuriteHelperUtil() {

    }

    public static void initialize() {
        DOCKER_COMPOSE_CONTAINER.withEnv("executable", "blob");
        DOCKER_COMPOSE_CONTAINER.withNetworkAliases(AzuriteHelperUtil.EXTRACTION_HOST);
        DOCKER_COMPOSE_CONTAINER.start();
        String dockerHost = DOCKER_COMPOSE_CONTAINER.getHost();

        BLOB_SERVICE_CLIENT = new BlobServiceClientBuilder()
            .connectionString(
                String.format(STORAGE_CONN_STRING,
                              dockerHost,
                              DOCKER_COMPOSE_CONTAINER.getMappedPort(AzuriteHelperUtil.CONTAINER_PORT))
            )
            .buildClient();
    }

    public static void stopDocker() {
        AzuriteHelperUtil.DOCKER_COMPOSE_CONTAINER.stop();
    }

}
