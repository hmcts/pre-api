package uk.gov.hmcts.reform.preapi.media;

import com.azure.storage.blob.BlobServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AzureFinalStorageService {
    private final BlobServiceClient finalStorageClient;

    @Autowired
    public AzureFinalStorageService(BlobServiceClient finalStorageClient) {
        this.finalStorageClient = finalStorageClient;
    }

    public boolean doesIsmFileExist(String containerName) {
        return finalStorageClient
            .getBlobContainerClient(containerName)
            .listBlobs()
            .stream()
            .anyMatch(blobItem -> blobItem.getName().endsWith(".ism"));
    }
}
