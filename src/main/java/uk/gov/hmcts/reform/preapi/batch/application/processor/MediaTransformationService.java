package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.AzureBlobService;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.media.MediaKind;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
public class MediaTransformationService {
    private LoggingService loggingService;

    private final AzureBlobService azureBlobService;
    private final MediaKind mediaKindService;

    @Autowired
    public MediaTransformationService(
        AzureBlobService azureBlobService, 
        MediaKind mediaKindService,
        LoggingService loggingService
    ) {
        this.azureBlobService = azureBlobService;
        this.mediaKindService = mediaKindService;
        this.loggingService = loggingService;
    }

    public void processMedia(String mp4FileName, UUID recordingId) {
        try {
            String localFilePath = downloadFile(mp4FileName);
            if (localFilePath == null) {
                loggingService.logError("Failed to download media file: %s", mp4FileName);
                return;
            }
            boolean copied = copyBlobBetweenContainers(
                Constants.Environment.SOURCE_CONTAINER, 
                Constants.Environment.SOURCE_ENVIRONMENT, 
                recordingId.toString(),
                Constants.Environment.INGEST_CONTAINER_STG,
                mp4FileName
            );
            
            if (!copied) {
                loggingService.logError("Failed to copy media file: %s", mp4FileName);
                return;
            }
            String ingestContainerName = recordingId + Constants.Environment.RECORDING_INPUT_CONTAINER_SUFFIX;
            transferMediaToIngestContainer(localFilePath, ingestContainerName, mp4FileName);

            processInMediaKind(recordingId, mp4FileName);

        } catch (Exception e) {
            loggingService.logError("Failed to process media: %s", e.getMessage());
        }
    }

    private boolean copyBlobBetweenContainers(
        String sourceContainer,
        String sourceEnvironment,
        String destContainer,
        String destEnvironment,
        String blobName
    ) {
        try {
            azureBlobService.copyBlob(
                sourceContainer, 
                sourceEnvironment, 
                destContainer, 
                destEnvironment, 
                blobName
            );
            return true;
        } catch (Exception e) {
            loggingService.logError("Error copying blob between containers: %s - %s", e.getMessage(), e);
            return false;
        }
    }

    private String downloadFile(String blobName) {
        try {
            InputStreamResource resource = azureBlobService.fetchSingleXmlBlob(
                Constants.Environment.SOURCE_CONTAINER, 
                Constants.Environment.SOURCE_ENVIRONMENT, 
                blobName
            );

            if (resource == null) {
                loggingService.logError("No resource found for blob: %s", blobName);
                return null;
            }

            return saveInputStreamToTempFile(resource);
        } catch (Exception e) {
            loggingService.logError("Error downloading media file: %s - %s", blobName, e);
            return null;
        }
    }

    /**
     * Saves an input stream to a temporary file.
     * 
     * @param resource Input stream resource
     * @return Path to the created temporary file
     * @throws IOException If file creation or writing fails
     */
    private String saveInputStreamToTempFile(InputStreamResource resource) throws IOException {
        File tempFile = File.createTempFile("media", ".mp4");
        try {
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                StreamUtils.copy(resource.getInputStream(), out);
                return tempFile.getAbsolutePath();
            }
        } catch (IOException e) {
            loggingService.logError("Error saving file locally: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Transfers media file to ingest container.
     * 
     * @param localFilePath Path to the local media file
     * @param ingestContainerName Name of the ingest container
     * @param fileName Name of the file to use in the container
     */
    private void transferMediaToIngestContainer(
        String localFilePath, 
        String ingestContainerName, 
        String fileName
    ) {
        try {
            azureBlobService.uploadBlob(
                localFilePath,
                ingestContainerName, 
                Constants.Environment.INGEST_CONTAINER_STG, 
                fileName
            );
        } catch (Exception e) {
            loggingService.logError("Failed to upload media to ingest container: %s - %s", ingestContainerName, e);
        }
    }

    private void processInMediaKind(UUID containerUUID, String mp4FileName) {
        try {
            String description = "Processing Vodafone file: " + mp4FileName + " in MediaKind";
            var tempAsset = containerUUID.toString().replace("-", "");
            var finalAsset = containerUUID.toString().replace("-", "") + "_output";

            GenerateAssetDTO generateAssetDTO = new GenerateAssetDTO();
            generateAssetDTO.setDescription(description);
            generateAssetDTO.setSourceContainer(containerUUID.toString() + "-input");
            generateAssetDTO.setDestinationContainer(containerUUID);
            generateAssetDTO.setTempAsset(tempAsset);
            generateAssetDTO.setFinalAsset(finalAsset);
            generateAssetDTO.setParentRecordingId(containerUUID);

            GenerateAssetResponseDTO response = mediaKindService.importAsset(generateAssetDTO);

        } catch (Exception e) {
            loggingService.logError("Failed to process media: %s - %s", e.getMessage(),e);
        }   
    }
}

