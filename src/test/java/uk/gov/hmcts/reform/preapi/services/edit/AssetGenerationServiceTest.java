package uk.gov.hmcts.reform.preapi.services.edit;

import com.azure.resourcemanager.mediaservices.models.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AssetGenerationService.class)
public class AssetGenerationServiceTest {

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private IMediaService mediaService;

    @Autowired
    private AssetGenerationService underTest;

    private static final UUID newRecordingId = UUID.randomUUID();
    private static final UUID originalRecordingId = UUID.randomUUID();

    @BeforeEach
    void setup() throws InterruptedException {
        when(azureFinalStorageService.getRecordingDuration(originalRecordingId)).thenReturn(Duration.ofMinutes(3));
        when(azureFinalStorageService.getMp4FileName(originalRecordingId.toString())).thenReturn("index.mp4");

        when(azureIngestStorageService.doesContainerExist(anyString())).thenReturn(true);

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);

        GenerateAssetResponseDTO importResponse = new GenerateAssetResponseDTO();
        importResponse.setJobStatus(JobState.FINISHED.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(importResponse);
    }

    @Test
    @DisplayName("Should throw not found when generate asset cannot find source container")
    void generateAssetSourceContainerNotFound() {
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(false);

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, originalRecordingId)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Source Container (" + sourceContainer + ") does not exist");

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verifyNoMoreInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should throw not found when generate asset cannot find source container's mp4")
    void generateAssetSourceContainerMp4NotFound() {
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        doThrow(new NotFoundException("MP4 file not found in container " + sourceContainer))
            .when(azureIngestStorageService).getMp4FileName(sourceContainer);

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, originalRecordingId)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: MP4 file not found in container " + sourceContainer);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verifyNoMoreInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should throw error when import asset fails when generating asset")
    void generateAssetImportAssetError() throws InterruptedException {
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        doThrow(new NotFoundException("Something went wrong")).when(mediaService)
            .importAsset(any(GenerateAssetDTO.class), eq(false));

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, originalRecordingId)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Something went wrong");

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(sourceContainer);
        verify(azureIngestStorageService, never()).markContainerAsSafeToDelete(sourceContainer);

        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
    }

    @Test
    @DisplayName("Should throw error when import asset fails (returning error) when generating asset")
    void generateAssetImportAssetReturnsError() throws InterruptedException {
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        var generateResponse = new GenerateAssetResponseDTO();
        generateResponse.setJobStatus(JobState.ERROR.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(generateResponse);

        var message = assertThrows(
            UnknownServerException.class,
            () -> underTest.generateAsset(newRecordingId, originalRecordingId)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Unknown Server Exception: Failed to generate asset for edit request: "
                           + originalRecordingId
                           + ", new recording: "
                           + newRecordingId);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(sourceContainer);
        verify(azureIngestStorageService, never()).markContainerAsSafeToDelete(sourceContainer);
        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
        verify(azureFinalStorageService, never()).getMp4FileName(any());
    }

    @Test
    @DisplayName("Should throw error when generating asset if get mp4 from final fails")
    void generateAssetGetMp4FinalNotFound() throws InterruptedException {
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        var generateResponse = new GenerateAssetResponseDTO();
        generateResponse.setJobStatus(JobState.FINISHED.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(generateResponse);
        doThrow(new NotFoundException("MP4 file not found in container " + newRecordingId))
            .when(azureFinalStorageService)
            .getMp4FileName(newRecordingId.toString());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, originalRecordingId)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: MP4 file not found in container " + newRecordingId);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsSafeToDelete(sourceContainer);
        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
        verify(azureFinalStorageService, times(1)).getMp4FileName(any());
    }
}
