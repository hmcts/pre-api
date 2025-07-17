package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
public abstract class AzureStorageService {

    protected final BlobServiceClient client;

    public AzureStorageService(BlobServiceClient client) {
        this.client = client;
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
}
