package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AzureIngestStorageService extends AzureStorageService {
    @Autowired
    public AzureIngestStorageService(BlobServiceClient ingestStorageClient) {
        super(ingestStorageClient);
    }

    public boolean doesValidAssetExist(String containerName) {
        return doesIsmFileExist(containerName) || doesBlobExist(containerName, "gc_state");
    }
}
