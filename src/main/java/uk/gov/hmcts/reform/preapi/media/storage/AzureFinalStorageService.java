package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;

import java.time.Duration;
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
            var containerName = recordingId.toString();
            var mpdFile = tryGetBlobWithExtension(containerName, ".mpd");
            if (mpdFile == null) {
                return null;
            }

            @Cleanup var inputStream = client.getBlobContainerClient(containerName).getBlobClient(mpdFile.getName())
                .openInputStream();

            var contents = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(inputStream)
                .getDocumentElement();
            contents.normalize();

            var duration = contents.getAttribute("mediaPresentationDuration");
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
}
