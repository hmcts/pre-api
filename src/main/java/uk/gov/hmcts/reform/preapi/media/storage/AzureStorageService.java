package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.time.OffsetDateTime;

@Slf4j
public abstract class AzureStorageService {
    protected final BlobServiceClient client;
    protected final AzureConfiguration azureConfiguration;

    public AzureStorageService(BlobServiceClient client, AzureConfiguration azureConfiguration) {
        this.client = client;
        this.azureConfiguration = azureConfiguration;
    }

    public String getStorageAccountName() {
        return client.getAccountName();
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

    protected String getBlobSasToken(String containerName,
                                  String blobName,
                                  OffsetDateTime expiryTime,
                                  BlobSasPermission permission) {
        if (!doesBlobExist(containerName, blobName)) {
            throw new NotFoundException("Blob not found in container " + containerName);
        }

        if (azureConfiguration.isUsingManagedIdentity()) {
            return "";
        }
        return "?" + client.getBlobContainerClient(containerName)
            .getBlobClient(blobName)
            .generateSas(new BlobServiceSasSignatureValues(expiryTime, permission));
    }

    public String getBlobUrlForCopy(String containerName, String blobName) {
        if (!doesBlobExist(containerName, blobName)) {
            throw new NotFoundException("Blob in container " + containerName);
        }
        return client.getBlobContainerClient(containerName).getBlobClient(blobName).getBlobUrl()
            + getBlobSasToken(
                containerName,
                blobName,
                OffsetDateTime.now().plusHours(1),
                new BlobSasPermission().setReadPermission(true));
    }

    public void copyBlob(String destinationContainerName, String destinationBlobName, String sourceUrl) {
        BlobContainerClient destinationContainerClient = client.getBlobContainerClient(destinationContainerName);
        if (!destinationContainerClient.exists()) {
            destinationContainerClient.create();
        }

        SyncPoller<BlobCopyInfo, Void> poller = destinationContainerClient.getBlobClient(destinationBlobName)
            .beginCopy(sourceUrl, null);
        poller.waitForCompletion();

        log.info(
            "Successfully copied blob '{}/{}' to {}",
            destinationContainerName,
            destinationBlobName,
            getStorageAccountName()
        );
    }
}
