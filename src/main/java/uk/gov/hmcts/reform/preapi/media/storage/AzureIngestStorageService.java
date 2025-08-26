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

    public boolean doesValidAssetExist(String containerName) {
        return doesIsmFileExist(containerName) || doesBlobExist(containerName, BLOB_GC_STATE);
    }

    public void markContainerAsProcessing(String containerName) {
        tagAllBlobsInContainer(containerName, TAG_KEY_STATUS, TAG_VALUE_PROCESSING);
    }

    public void markContainerAsSafeToDelete(String containerName) {
        tagAllBlobsInContainer(containerName, TAG_KEY_STATUS, TAG_VALUE_SAFE_TO_DELETE);
    }
}
