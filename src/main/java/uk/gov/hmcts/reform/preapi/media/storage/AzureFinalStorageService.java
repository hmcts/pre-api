package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.specialized.BlobInputStream;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;

@Slf4j
@Service
public class AzureFinalStorageService extends AzureStorageService {
    @Autowired
    public AzureFinalStorageService(BlobServiceClient finalStorageClient, AzureConfiguration azureConfiguration) {
        super(finalStorageClient, azureConfiguration);
    }

    public Duration getRecordingDuration(UUID recordingId) {
        try {
            String containerName = recordingId.toString();
            BlobItem mpdFile = tryGetBlobWithExtension(containerName, ".mpd");
            if (mpdFile == null) {
                return null;
            }

            @Cleanup BlobInputStream inputStream = client.getBlobContainerClient(containerName)
                .getBlobClient(mpdFile.getName())
                .openInputStream();

            Element contents = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(inputStream)
                .getDocumentElement();
            contents.normalize();

            String duration = contents.getAttribute("mediaPresentationDuration");
            return Duration.parse(duration);
        } catch (Exception e) {
            log.error("Something went wrong when attempting to get recording's duration: {}", e.getMessage());
            return null;
        }
    }

    private BlobItem tryGetBlobWithExtension(String containerName, String extension) {
        return doesContainerExist(containerName)
            ? client
                .getBlobContainerClient(containerName)
                .listBlobs()
                .stream()
                .filter(blobItem -> blobItem.getName().endsWith(extension))
                .findFirst()
                .orElse(null)
            : null;
    }

    public String generateReadSasUrl(String containerName, String blobName) {
        if (!doesBlobExist(containerName, blobName)) {
            throw new NotFoundException("Blob in container " + containerName);
        }
        return client.getBlobContainerClient(containerName).getBlobClient(blobName).getBlobUrl()
            + getBlobSasToken(
            containerName,
            blobName,
            OffsetDateTime.now().plusHours(2),
            new BlobSasPermission().setReadPermission(true)
        );
    }
}
