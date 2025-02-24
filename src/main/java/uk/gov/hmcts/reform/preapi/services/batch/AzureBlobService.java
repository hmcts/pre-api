package uk.gov.hmcts.reform.preapi.services.batch;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


@Service
public class AzureBlobService {

    private Map<String, BlobServiceClient> clients = new HashMap<>();


    @Autowired
    public AzureBlobService(@Value("${azure.storage.connectionString}") String devConnectionString,
                            @Value("${azure.ingestStorage.connectionString}")String ingestStgConnectionString,
                            @Value("${azure.finalStorage.connectionString}") String finalStgConnectionString
    ) {
        
        clients.put("dev", new BlobServiceClientBuilder()
                            .connectionString(devConnectionString)
                            .buildClient());
        clients.put("stagingIngest", new BlobServiceClientBuilder()
                            .connectionString(ingestStgConnectionString)
                            .buildClient());
        clients.put("stagingFinal", new BlobServiceClientBuilder()
                            .connectionString(finalStgConnectionString)
                            .buildClient());
    }

    private BlobServiceClient getClient(String environment) {
        return clients.get(environment);
    }

    public void listBlobs(String containerName, String environment) {
        try {
            BlobContainerClient containerClient = getClient(environment).getBlobContainerClient(containerName);

            if (!containerClient.exists()) {
                Logger.getAnonymousLogger().warning("AzureBlobFetcher: Container does not exist - " + containerName);
                return;
            }
        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("AzureBlobFetcher: Error accessing Azure Blob Storage: " 
                + e.getMessage() + e);
        }
    }

    public List<String> fetchBlobNames(String containerName, String environment) {
        List<String> blobNames = new ArrayList<>();

        BlobContainerClient containerClient = getClient(environment).getBlobContainerClient(containerName);

        for (BlobItem blobItem : containerClient.listBlobs()) {
            String blobName = blobItem.getName();
            if (blobName.endsWith(".xml")) {
                blobNames.add(blobName);
            }
        }

        return blobNames;
    }

    public InputStreamResource fetchSingleXmlBlob(String containerName, String environment, String blobName) {
        try {
            BlobContainerClient containerClient = getClient(environment).getBlobContainerClient(containerName);
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

    public List<String> fetchBlobNamesPaginated(String containerName, String environment, int offset, int limit) {
        List<String> paginatedBlobNames = new ArrayList<>();
        BlobContainerClient containerClient = getClient(environment).getBlobContainerClient(containerName);

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

    public boolean uploadBlob(String localFileName, String containerName, String environment, String uploadFileName) {
        try {
            var file = new File(localFileName);
            var containerClient = getClient(environment).getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
            }
            var blobClient = containerClient.getBlobClient(uploadFileName);
            blobClient.upload(new FileInputStream(file), file.length(), true);
      
            return true;
        } catch (IOException e) {
            Logger.getAnonymousLogger().warning("Failed to upload to storage: " 
                + containerName + uploadFileName + e);
        }
        return false;
    }

}
