package uk.gov.hmcts.reform.preapi.services.edit;

import com.azure.resourcemanager.mediaservices.models.JobState;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.util.UUID;

@Service
public class AssetGenerationService {

    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final MediaServiceBroker mediaServiceBroker;

    @Autowired
    public AssetGenerationService(final AzureIngestStorageService azureIngestStorageService,
                                  final AzureFinalStorageService azureFinalStorageService,
                                  final MediaServiceBroker mediaServiceBroker) {
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.mediaServiceBroker = mediaServiceBroker;
    }

    @Transactional
    public String generateAsset(UUID newRecordingId, EditRequest request) throws InterruptedException {
        String sourceContainer = newRecordingId + "-input";
        if (!azureIngestStorageService.doesContainerExist(sourceContainer)) {
            throw new NotFoundException("Source Container (" + sourceContainer + ") does not exist");
        }
        // throws 404 when doesn't exist
        azureIngestStorageService.getMp4FileName(sourceContainer);
        azureIngestStorageService.markContainerAsProcessing(sourceContainer);
        String assetName = newRecordingId.toString().replace("-", "");

        azureFinalStorageService.createContainerIfNotExists(newRecordingId.toString());

        if (request.getSourceRecording() == null) {
            throw new BadRequestException("Source Recording was null for edit request " + request.getId());
        }

        UUID sourceRecordingId = request.getSourceRecording().getId();
        GenerateAssetDTO generateAssetDto = GenerateAssetDTO.builder()
            .sourceContainer(sourceContainer)
            .destinationContainer(newRecordingId)
            .tempAsset(assetName)
            .finalAsset(assetName + "_output")
            .parentRecordingId(sourceRecordingId)
            .description("Edit of " + sourceRecordingId.toString().replace("-", ""))
            .build();

        GenerateAssetResponseDTO result = mediaServiceBroker.getEnabledMediaService()
            .importAsset(generateAssetDto, false);

        if (!result.getJobStatus().equals(JobState.FINISHED.toString())) {
            throw new UnknownServerException("Failed to generate asset for edit request: "
                                                 + sourceRecordingId
                                                 + ", new recording: "
                                                 + newRecordingId);
        }
        azureIngestStorageService.markContainerAsSafeToDelete(sourceContainer);
        return azureFinalStorageService.getMp4FileName(newRecordingId.toString());
    }

}
