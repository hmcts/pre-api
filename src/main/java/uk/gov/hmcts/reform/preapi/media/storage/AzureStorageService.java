package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class AzureStorageService {
    protected final BlobServiceClient client;
    protected final AzureConfiguration azureConfiguration;

    protected AzureStorageService(BlobServiceClient client, AzureConfiguration azureConfiguration) {
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

    public BlobClient getBlob(String containerName, String blobName) {
        if (!doesBlobExist(containerName, blobName)) {
            throw new NotFoundException("Container: " + containerName + ", with blob: " + blobName);
        }
        return client.getBlobContainerClient(containerName)
            .getBlobClient(blobName);
    }

    public boolean downloadBlob(String containerName, String blobName, String downloadPath) {
        try {
            log.info("Attempting to download blob {} from container {}", blobName, containerName);
            getBlob(containerName, blobName).downloadToFile(downloadPath, true);
            return true;
        } catch (Exception e) {
            log.error("Error downloading blob {} from container {}", blobName, containerName, e);
            return false;
        }
    }

    public boolean uploadBlob(String localFileName, String containerName, String uploadFileName) {
        try {
            var file = new File(localFileName);
            var containerClient = client.createBlobContainerIfNotExists(containerName);
            var blobClient = containerClient.getBlobClient(uploadFileName);
            blobClient.upload(new FileInputStream(file), file.length(), true);
            log.info("Successfully uploaded to ingest storage: {}/{}", containerName, uploadFileName);
            return true;
        } catch (IOException e) {
            log.error("Failed to upload to ingest storage: {}/{}", containerName, uploadFileName, e);
        }
        return false;
    }

    public void createContainerIfNotExists(String containerName) {
        log.info("Creating container: {}", containerName);
        client.createBlobContainerIfNotExists(containerName);
    }

    protected String getBlobSasToken(String containerName,
                                     String blobName,
                                     OffsetDateTime expiryTime,
                                     BlobSasPermission permission) {
        if (azureConfiguration.isUsingManagedIdentity()) {
            UserDelegationKey delegationKey = client.getUserDelegationKey(
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(2)
            );
            return "?" + client.getBlobContainerClient(containerName)
                .getBlobClient(blobName)
                .generateUserDelegationSas(new BlobServiceSasSignatureValues(expiryTime, permission), delegationKey);
        }
        return "?" + client.getBlobContainerClient(containerName)
            .getBlobClient(blobName)
            .generateSas(new BlobServiceSasSignatureValues(expiryTime, permission));
    }

    public void tagAllBlobsInContainer(String containerName, String tagKey, String tagValue) {
        log.info("Setting all blob's tags in container '{}': '{}' to '{}'", containerName, tagKey, tagValue);
        try {
            BlobContainerClient containerClient = client.getBlobContainerClient(containerName);
            containerClient.listBlobs()
                .stream()
                .map(BlobItem::getName)
                .map(containerClient::getBlobClient)
                .forEach(blob -> setBlobTag(blob, tagKey, tagValue));
        } catch (Exception e) {
            log.error(
                "Failed to set all blob's tags (key = {}, value = {}) in container '{}'",
                tagKey,
                tagValue,
                containerName,
                e
            );
        }
    }

    protected void setBlobTag(BlobClient blob, String tagKey, String tagValue) {
        Map<String, String> currentTags = blob.getTags();
        Map<String, String> newTags = new HashMap<>(currentTags);
        newTags.put(tagKey, tagValue);
        blob.setTags(newTags);
    }

    public void copyBlob(String destinationContainerName, String destinationBlobName, String sourceUrl) {
        copyBlobOverwritable(destinationContainerName, destinationBlobName, sourceUrl, true);
    }

    public void copyBlobOverwritable(String destinationContainerName,
                                     String destinationBlobName,
                                     String sourceUrl,
                                     boolean overwrite) {
        BlobContainerClient destinationContainerClient = client.getBlobContainerClient(destinationContainerName);
        if (!destinationContainerClient.exists()) {
            destinationContainerClient.create();
        }

        if (!overwrite) {
            if (destinationContainerClient.getBlobClient(destinationBlobName).exists()) {
                log.info(
                    "Skipped copying blob '{}/{}' to {}. Already exists.",
                    destinationContainerName,
                    destinationBlobName,
                    getStorageAccountName()
                );
                return;
            }
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
}
