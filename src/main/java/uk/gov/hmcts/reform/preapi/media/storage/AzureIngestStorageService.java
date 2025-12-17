package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;

@Service
public class AzureIngestStorageService extends AzureStorageService {
    private static final String BLOB_GC_STATE = "gc_state";
    private static final String TAG_KEY_STATUS = "status";
    private static final String TAG_VALUE_PROCESSING = "processing";
    private static final String TAG_VALUE_SAFE_TO_DELETE = "safe_to_delete";

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

    public boolean sectionFileExist(String containerName) {
        return doesContainerExist(containerName)
            && client
            .getBlobContainerClient(containerName)
            .listBlobs()
            .stream()
            .anyMatch(blobItem -> blobItem.getName().equalsIgnoreCase("0/section"));
    }

    // Deprecated, do not rely on MK internal state to determine recording status
    @Deprecated
    public boolean doesValidAssetExist(String containerName) {
        return doesIsmFileExist(containerName) || doesBlobExist(containerName, BLOB_GC_STATE);
    }
}
