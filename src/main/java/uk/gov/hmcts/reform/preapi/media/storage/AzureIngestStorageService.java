package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Optional;

@Slf4j
@Service
public class AzureIngestStorageService extends AzureStorageService {
    private static final String TAG_KEY_STATUS = "status";
    private static final String TAG_VALUE_PROCESSING = "processing";
    private static final String TAG_VALUE_SAFE_TO_DELETE = "safe_to_delete";
    private static final String TAG_VALUE_ACTIVE_MONITORING = "active_monitoring";

    @Autowired
    public AzureIngestStorageService(BlobServiceClient ingestStorageClient, AzureConfiguration azureConfiguration) {
        super(ingestStorageClient, azureConfiguration);
    }

    public void markContainerAsProcessing(String containerName) {
        tagAllBlobsInContainer(containerName, TAG_KEY_STATUS, TAG_VALUE_PROCESSING);
    }

    public void markContainerAsSafeToDelete(String containerName) {
        tagAllBlobsInContainer(containerName, TAG_KEY_STATUS, TAG_VALUE_SAFE_TO_DELETE);
    }

    // QUICK DRAFT: no tests written yet
    public boolean latestFileWasWithinLast(String containerName, int seconds) {
        if (!doesContainerExist(containerName)) {
            log.error("Active monitoring tried to monitor a container that does not exist.");
            return true;
        }
        PagedIterable<BlobItem> blobItems = client.getBlobContainerClient(containerName).listBlobs();

        Optional<OffsetDateTime> lastModified = blobItems.stream()
            .max(Comparator.comparing(a -> a.getProperties().getLastModified()))
            .map(a -> a.getProperties().getLastModified());

        if (lastModified.isEmpty()) {
            log.error("Active monitoring found no blob for container {}", containerName);
            return true;
        }

        if (lastModified.get().isBefore(OffsetDateTime.now().minusSeconds(seconds))) {
            log.info("ACTIVE MONITORING ALERT: container {} last received data at {} (more than {} seconds ago)",
                     containerName, lastModified, seconds);
            return false;
        }

        log.debug("Active monitoring: container {} is still receiving data at {}",  containerName, lastModified);
        return true;
    }

    public boolean sectionFileExist(String containerName) {
        return doesContainerExist(containerName)
            && client
            .getBlobContainerClient(containerName)
            .listBlobs()
            .stream()
            .anyMatch(blobItem -> blobItem.getName().equalsIgnoreCase("0/section"));
    }
}
