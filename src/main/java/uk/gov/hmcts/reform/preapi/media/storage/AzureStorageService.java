package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.HashMap;

@Slf4j
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

    public void tagAllBlobsInContainer(String containerName, String tagKey, String tagValue) {
        log.info("Setting all blob's tags in container '{}': '{}' to '{}'", containerName, tagKey, tagValue);
        var containerClient = client.getBlobContainerClient(containerName);
        containerClient.listBlobs()
            .stream()
            .map(BlobItem::getName)
            .map(containerClient::getBlobClient)
            .forEach(blob -> setBlobTag(blob, tagKey, tagValue));
    }

    protected void setBlobTag(BlobClient blob, String tagKey, String tagValue) {
        var currentTags = blob.getTags();
        var newTags = new HashMap<>(currentTags);
        newTags.put(tagKey, tagValue);
        blob.setTags(newTags);
    }
}
