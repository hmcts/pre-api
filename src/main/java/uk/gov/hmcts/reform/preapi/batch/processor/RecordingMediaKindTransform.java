package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import uk.gov.hmcts.reform.preapi.services.batch.AzureBlobService;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.media.MediaKind;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class RecordingMediaKindTransform {
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
            String containerNameIngest = recordingId.toString() + "-input";
            String sourceEnvironment = "dev";
            String sourceEnvironmentContainerName = "pre-vodafone-spike";
            String ingestEnvironment = "stagingIngest";
            String finalEnvironment = "stagingFinal";

            // Fetch and transfer MP4 from 'presadev' to 'ingest'
            String localFilePath = downloadFile(mp4FileName, sourceEnvironment, sourceEnvironmentContainerName);
            if (localFilePath != null) {
                azureBlobService.uploadBlob(localFilePath, containerNameIngest, ingestEnvironment, mp4FileName);
            }

            // Process the media in 'ingest'
            processInMediaKind(recordingId, mp4FileName);

            // Once processed, transfer from 'ingest' to 'final'
            if (localFilePath != null) {
                String processedFilePath = localFilePath.replace(".mp4", "_processed.mp4");
                azureBlobService.uploadBlob(processedFilePath, containerName, finalEnvironment, mp4FileName);
            }

            Logger.getAnonymousLogger().info("Media processing complete for: " + mp4FileName);
        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Failed to process media: " + e.getMessage());
        }
    }

    private String downloadFile(String blobName, String environment, String containerName) {
        InputStreamResource resource = azureBlobService.fetchSingleXmlBlob(containerName, environment, blobName);
        if (resource == null) {
            Logger.getAnonymousLogger().warning("Failed to fetch file: " + blobName);
            return null;
        }

        try {
            File tempFile = File.createTempFile("media", ".mp4");
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                StreamUtils.copy(resource.getInputStream(), out);
                return tempFile.getAbsolutePath();
            }
        } catch (IOException e) {
            Logger.getAnonymousLogger().severe("Error saving file locally: " + e.getMessage());
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
            Logger.getAnonymousLogger().info("Asset Import Job State: " + response.getJobStatus());
        } catch (Exception e) {
            Logger.getAnonymousLogger().severe("Failed to process media: " + e.getMessage());
        }
        
    }
}

