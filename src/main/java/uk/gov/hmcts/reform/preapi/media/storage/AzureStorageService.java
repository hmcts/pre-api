package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.Optional;

@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
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

    public String getMp4FileName(String containerName) {
        Optional<BlobItem> blob = client.getBlobContainerClient(containerName)
                         .listBlobs()
                         .stream()
                         .filter(blobItem -> blobItem.getName().endsWith(".mp4"))
                         .findFirst();
        if (blob.isPresent()) {
            return blob.get().getName();
        }
        throw new NotFoundException("MP4 file not found in container " + containerName);
    }

    public String tryGetMp4FileName(String containerName) {
        try {
            return getMp4FileName(containerName);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean doesBlobExist(String containerName, String blobName) {
        return doesContainerExist(containerName)
            && client
                .getBlobContainerClient(containerName)
                .listBlobs()
                .stream()
                .anyMatch(blobItem -> blobItem.getName().equalsIgnoreCase(blobName));
    }
}
