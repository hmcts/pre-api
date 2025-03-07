package uk.gov.hmcts.reform.preapi.batch.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import uk.gov.hmcts.reform.preapi.batch.services.AzureBlobService;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.media.MediaKind;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
public class MediaTransformationService {
    private static final Logger logger = LoggerFactory.getLogger(MediaTransformationService.class);

    private static final String SOURCE_CONTAINER = "pre-vodafone-spike";
    private static final String SOURCE_ENVIRONMENT = "dev";
    private static final String INGEST_ENVIRONMENT = "stagingIngest";
    private static final String INPUT_CONTAINER_SUFFIX = "-input";
    
    private final AzureBlobService azureBlobService;
    private final MediaKind mediaKindService;

    @Autowired
    public MediaTransformationService(AzureBlobService azureBlobService, MediaKind mediaKindService) {
        this.azureBlobService = azureBlobService;
        this.mediaKindService = mediaKindService;
    }

    public void processMedia(String mp4FileName, UUID recordingId) {
        try {
            String localFilePath = downloadFile(mp4FileName);
            if (localFilePath == null) {
                logger.error("Failed to download media file: {}", mp4FileName);
                return;
            }
            boolean copied = copyBlobBetweenContainers(
                SOURCE_CONTAINER, 
                SOURCE_ENVIRONMENT, 
                recordingId.toString(),
                INGEST_ENVIRONMENT,
                mp4FileName
            );
            
            if (!copied) {
                logger.error("Failed to copy media file: {}", mp4FileName);
                return;
            }
            String ingestContainerName = recordingId + INPUT_CONTAINER_SUFFIX;
            transferMediaToIngestContainer(localFilePath, ingestContainerName, mp4FileName);

            processInMediaKind(recordingId, mp4FileName);

        } catch (Exception e) {
            logger.error("Failed to process media: {}", e.getMessage());
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
            logger.error("Error copying blob between containers: {}", e.getMessage(), e);
            return false;
        }
    }

    private String downloadFile(String blobName) {
        try {
            InputStreamResource resource = azureBlobService.fetchSingleXmlBlob(
                SOURCE_CONTAINER, SOURCE_ENVIRONMENT, blobName
            );

            if (resource == null) {
                logger.error("No resource found for blob: {}", blobName);
                return null;
            }

            return saveInputStreamToTempFile(resource);
        } catch (Exception e) {
            logger.error("Error downloading media file: {}", blobName, e);
            return null;
        }
    }

    /**
     * Saves an input stream to a temporary file.
     * 
     * @param resource Input stream resource
     * @param fileExtension File extension
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
            logger.error("Error saving file locally: {}", e.getMessage());
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
            
            azureBlobService.uploadBlob(localFilePath, ingestContainerName, INGEST_ENVIRONMENT, fileName);
        } catch (Exception e) {
            logger.error("Failed to upload media to ingest container: {}", ingestContainerName, e);
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
            logger.error("Failed to process media: {}", e.getMessage(),e);
        }   
    }
}

