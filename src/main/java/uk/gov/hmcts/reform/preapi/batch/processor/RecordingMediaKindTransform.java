package uk.gov.hmcts.reform.preapi.batch.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import uk.gov.hmcts.reform.preapi.config.batch.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.services.batch.AzureBlobService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
public class RecordingMediaKindTransform {
    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    private final AzureBlobService azureBlobService;
    private final MediaKind mediaKindService;

    @Autowired
    public RecordingMediaKindTransform(AzureBlobService azureBlobService, MediaKind mediaKindService) {
        this.azureBlobService = azureBlobService;
        this.mediaKindService = mediaKindService;
    }

    public void processMedia(String mp4FileName, UUID recordingId) {
        try {
            String containerName = recordingId.toString();
            String containerNameIngest = containerName + "-input";
            String sourceEnvironmentContainerName = "pre-vodafone-spike";
            String sourceEnvironment = "dev";
            String ingestEnvironment = "stagingIngest";

            // Fetch and transfer MP4 from 'presadev' to 'ingest'
            String localFilePath = downloadFile(mp4FileName, sourceEnvironment, sourceEnvironmentContainerName);
            if (localFilePath != null) {
                azureBlobService.uploadBlob(localFilePath, containerNameIngest, ingestEnvironment, mp4FileName);
            }

            // Process the media in 'ingest' and upload to 'final'
            processInMediaKind(recordingId, mp4FileName);

        } catch (Exception e) {
            logger.error("Failed to process media: {}", e.getMessage());
        }
    }

    private String downloadFile(String blobName, String environment, String containerName) {
        InputStreamResource resource = azureBlobService.fetchSingleXmlBlob(containerName, environment, blobName);
        if (resource == null) {
            logger.error("Failed to fetch file: {}", blobName);
            return null;
        }

        try {
            File tempFile = File.createTempFile("media", ".mp4");
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                StreamUtils.copy(resource.getInputStream(), out);
                return tempFile.getAbsolutePath();
            }
        } catch (IOException e) {
            logger.error("Error saving file locally: {}", e.getMessage());
            return null;
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

