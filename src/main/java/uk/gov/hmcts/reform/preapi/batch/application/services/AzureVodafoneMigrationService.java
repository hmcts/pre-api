package uk.gov.hmcts.reform.preapi.batch.application.services;

import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;

import java.util.logging.Logger;

@Service
@Slf4j
public class AzureVodafoneMigrationService {

    private final BlobServiceClient vodafoneStorageClient;
    private final BlobServiceClient ingestStorageClient;

    @Autowired
    public AzureVodafoneMigrationService(AzureConfiguration configuration) {
        this.vodafoneStorageClient = configuration.vodafoneStorageClient();
        this.ingestStorageClient = configuration.ingestStorageClient();
    }

    public void copyBlob(
        String sourceContainer,
        String destContainer,
        String blobName
    ) {
        try {
            var sourceContainerClient = this.vodafoneStorageClient.getBlobContainerClient(sourceContainer);
            var sourceBlobClient = sourceContainerClient.getBlobClient(blobName);

            var destContainerClient = this.ingestStorageClient.getBlobContainerClient(destContainer);
            var destBlobClient = destContainerClient.getBlobClient(blobName);

            if (!destContainerClient.exists()) {
                destContainerClient.create();
            }

            String sourceUrl = sourceBlobClient.getBlobUrl();
            SyncPoller<BlobCopyInfo, Void> poller = destBlobClient.beginCopy(sourceUrl, null);

            poller.waitForCompletion();

            log.info(
                "Successfully copied blob '{}' from vodafone/{} to ingest/{}",
                blobName,
                sourceContainer,
                destContainer
            );

        } catch (Exception e) {
            Logger.getAnonymousLogger().warning("Error copying blob: {}" + e.getMessage() + e);
            throw new RuntimeException(
                String.format("Failed to copy blob '%s' from '%s' to '%s': %s",
                    blobName, sourceContainer, destContainer, e.getMessage()),
                e
            );
        }
    }

}
