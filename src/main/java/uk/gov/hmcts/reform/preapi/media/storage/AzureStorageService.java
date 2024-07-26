package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

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

    public boolean doesContainerExist(String containerName) {
        return client.getBlobContainerClient(containerName).exists();
    }

    public String getMp4FileName(String containerName) {
        var blob = client.getBlobContainerClient(containerName)
                         .listBlobs()
                         .stream()
                         .filter(blobItem -> blobItem.getName().endsWith(".mp4"))
                         .findFirst();
        if (blob.isPresent()) {
            return blob.get().getName();
        }
        throw new NotFoundException("MP4 file not found in container " + containerName);
    }

    public boolean doesBlobExist(String containerName, String blobName) {
        return client
            .getBlobContainerClient(containerName)
            .listBlobs()
            .stream()
            .anyMatch(blobItem -> blobItem.getName().equalsIgnoreCase(blobName));
    }
}
