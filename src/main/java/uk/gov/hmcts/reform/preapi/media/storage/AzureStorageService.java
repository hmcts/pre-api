package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;

public abstract class AzureStorageService {

    protected final BlobServiceClient client;

    public AzureStorageService(BlobServiceClient client) {
        this.client = client;
    }

    public boolean doesContainerExist(String containerName) {
        return client.getBlobContainerClient(containerName).exists();
    }

    public boolean doesIsmFileExist(String containerName) {
        return doesContainerExist(containerName)
            && client
            .getBlobContainerClient(containerName)
            .listBlobs()
            .stream()
            .anyMatch(blobItem -> blobItem.getName().endsWith(".ism"));
    }

    public boolean doesBlobExist(String containerName, String blobName) {
        return client
            .getBlobContainerClient(containerName)
            .listBlobs()
            .stream()
            .anyMatch(blobItem -> blobItem.getName().equalsIgnoreCase(blobName));
    }
}
