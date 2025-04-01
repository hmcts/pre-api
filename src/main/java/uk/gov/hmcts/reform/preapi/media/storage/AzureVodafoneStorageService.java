package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AzureVodafoneStorageService extends AzureStorageService {
    @Autowired
    public AzureVodafoneStorageService(BlobServiceClient vodafoneStorageClient) {
        super(vodafoneStorageClient);
    }

    public List<String> fetchBlobNames(String containerName) {
        var containerClient = client.getBlobContainerClient(containerName);

        return containerClient.listBlobs().stream()
                              .map(BlobItem::getName)
                              .filter(name -> name.endsWith(".xml"))
                              .toList();
    }

    public InputStreamResource fetchSingleXmlBlob(String containerName, String blobName) {
        try {
            BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            if (!blobClient.exists()) {
                log.warn("Blob not found: {}", blobName);
                return null;
            }

            return new InputStreamResource(blobClient.openInputStream(), blobName);
        } catch (Exception e) {
            log.error("Failed to fetch blob: {} - {}", blobName, e.getMessage());
            return null;
        }
    }

}
