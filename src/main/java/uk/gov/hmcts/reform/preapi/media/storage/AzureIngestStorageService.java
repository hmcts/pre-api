package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Slf4j
@Service
public class AzureIngestStorageService extends AzureStorageService {
    @Autowired
    public AzureIngestStorageService(BlobServiceClient ingestStorageClient) {
        super(ingestStorageClient);
    }

    public boolean doesValidAssetExist(String containerName) {
        return doesIsmFileExist(containerName) || doesBlobExist(containerName, "gc_state");
    }

    public void markContainerAsProcessing(String containerName) {
        tagAllBlobsInContainer(containerName, "status", "processing");
    }

    public void markContainerAsSafeToDelete(String containerName) {
        tagAllBlobsInContainer(containerName, "status", "safe_to_delete");
    }

    protected void tagAllBlobsInContainer(String containerName, String tagKey, String tagValue) {
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
