package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class AzureVodafoneStorageService extends AzureStorageService {
    @Autowired
    public AzureVodafoneStorageService(BlobServiceClient vodafoneStorageClient) {
        super(vodafoneStorageClient);
    }

    public List<String> fetchBlobNames(String containerName) {
        List<String> blobNames = new ArrayList<>();

        BlobContainerClient containerClient = client.getBlobContainerClient(containerName);

        for (BlobItem blobItem : containerClient.listBlobs()) {
            String blobName = blobItem.getName();
            if (blobName.endsWith(".xml")) {
                blobNames.add(blobName);
            }
        }

        return blobNames;
    }

    public InputStreamResource fetchSingleXmlBlob(String containerName, String blobName) {
        try {
            BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            if (!blobClient.exists()) {
                Logger.getAnonymousLogger().warning("Blob not found: " + blobName);
                return null;
            }

            return new InputStreamResource(blobClient.openInputStream(), blobName);
        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Failed to fetch blob: " + blobName + " - " + e.getMessage());
            return null;
        }
    }

    public List<String> fetchBlobNamesPaginated(String containerName, int offset, int limit) {
        List<String> paginatedBlobNames = new ArrayList<>();
        BlobContainerClient containerClient = client.getBlobContainerClient(containerName);

        if (!containerClient.exists()) {
            Logger.getAnonymousLogger().warning("Container does not exist: " + containerName);
            return paginatedBlobNames;
        }

        int currentIndex = 0;
        for (BlobItem blobItem : containerClient.listBlobs()) {
            String blobName = blobItem.getName();
            if (blobName.endsWith(".xml")) {
                if (currentIndex >= offset && currentIndex < offset + limit) {
                    paginatedBlobNames.add(blobItem.getName());
                }
                currentIndex++;

                if (currentIndex >= offset + limit) {
                    break;
                }
            }
        }

        return paginatedBlobNames;
    }

}
