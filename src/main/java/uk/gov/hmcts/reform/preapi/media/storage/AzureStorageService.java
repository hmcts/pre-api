package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;

public abstract class AzureStorageService {

    protected final BlobServiceClient client;

    public AzureStorageService(BlobServiceClient client) {
        this.client = client;
    }

    public boolean doesIsmFileExist(String containerName) {
        return client
            .getBlobContainerClient(containerName)
            .listBlobs()
            .stream()
            .anyMatch(blobItem -> blobItem.getName().endsWith(".ism"));
    }
}