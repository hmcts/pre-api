package uk.gov.hmcts.reform.preapi.services.edit;

import com.azure.resourcemanager.mediaservices.models.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AssetGenerationService.class)
class AssetGenerationServiceTest {

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private IMediaService mediaService;

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private EditRequest mockEditRequest;

    @Autowired
    private AssetGenerationService underTest;

    private static final UUID mockRecordingId = UUID.randomUUID();
    private static final UUID mockEditRequestId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        when(mockRecording.getId()).thenReturn(mockRecordingId);

        when(azureFinalStorageService.getRecordingDuration(mockRecordingId)).thenReturn(Duration.ofMinutes(3));
        when(azureFinalStorageService.getMp4FileName(mockRecordingId.toString())).thenReturn("filename");

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);

        when(mockEditRequest.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequest.getSourceRecording()).thenReturn(mockRecording);
    }

    @Test
    @DisplayName("Should generate asset")
    void generateAssetSourceSuccess() throws InterruptedException {
        UUID newRecordingId = UUID.randomUUID();
        String sourceContainer = newRecordingId + "-input";

        GenerateAssetResponseDTO importResponse = new GenerateAssetResponseDTO();
        importResponse.setJobStatus(JobState.FINISHED.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false)))
            .thenReturn(importResponse);
        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);

        when(azureFinalStorageService.getMp4FileName(newRecordingId.toString())).thenReturn("yay it worked");

        String generatedFilename;

        try {
            generatedFilename = underTest.generateAsset(newRecordingId, mockEditRequest);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        assertThat(generatedFilename).isEqualTo("yay it worked");

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(sourceContainer);
        verify(azureFinalStorageService, times(1))
            .createContainerIfNotExists(newRecordingId.toString());

        ArgumentCaptor<GenerateAssetDTO> captor = ArgumentCaptor.forClass(GenerateAssetDTO.class);
        verify(mediaService, times(1))
            .importAsset(captor.capture(), any(Boolean.class));

        String expectedAssetName = newRecordingId.toString().replace("-", "");
        GenerateAssetDTO generatedAsset = captor.getValue();
        assertThat(generatedAsset.getSourceContainer()).isEqualTo(sourceContainer);
        assertThat(generatedAsset.getDestinationContainer()).isEqualTo(newRecordingId);
        assertThat(generatedAsset.getTempAsset()).isEqualTo(expectedAssetName);
        assertThat(generatedAsset.getFinalAsset()).isEqualTo(expectedAssetName + "_output");
        assertThat(generatedAsset.getParentRecordingId()).isEqualTo(mockRecordingId);
        assertThat(generatedAsset.getDescription()).isNotBlank();

        verify(azureIngestStorageService, times(1)).markContainerAsSafeToDelete(sourceContainer);
        verify(azureFinalStorageService, times(1)).getMp4FileName(newRecordingId.toString());
        verifyNoMoreInteractions(azureIngestStorageService);
        verifyNoMoreInteractions(azureFinalStorageService);
        verifyNoMoreInteractions(mediaService);
    }

    @Test
    @DisplayName("Should throw not found when generate asset cannot find source container")
    void generateAssetSourceContainerNotFound() {
        EditRequest editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        UUID newRecordingId = UUID.randomUUID();
        String sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(false);

        String message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Source Container (" + sourceContainer + ") does not exist");

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verifyNoMoreInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should throw error when import asset fails when generating asset")
    void generateAssetImportAssetError() throws InterruptedException {
        EditRequest editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        UUID newRecordingId = UUID.randomUUID();
        String sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        doThrow(new NotFoundException("Something went wrong")).when(mediaService)
            .importAsset(any(GenerateAssetDTO.class), eq(false));

        String message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
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
        EditRequest editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        UUID newRecordingId = UUID.randomUUID();
        String sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        GenerateAssetResponseDTO generateResponse = new GenerateAssetResponseDTO();
        generateResponse.setJobStatus(JobState.ERROR.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(generateResponse);

        String message = assertThrows(
            UnknownServerException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Unknown Server Exception: Failed to generate asset for edit request: "
                           + editRequest.getSourceRecording().getId()
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
        EditRequest editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        UUID newRecordingId = UUID.randomUUID();
        String sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        GenerateAssetResponseDTO generateResponse = new GenerateAssetResponseDTO();
        generateResponse.setJobStatus(JobState.FINISHED.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(generateResponse);
        doThrow(new NotFoundException("MP4 file not found in container " + newRecordingId))
            .when(azureFinalStorageService)
            .getMp4FileName(newRecordingId.toString());

        String message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: MP4 file not found in container " + newRecordingId);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsSafeToDelete(sourceContainer);
        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
        verify(azureFinalStorageService, times(1)).getMp4FileName(any());
    }

    @Test
    @DisplayName("Should throw not found when generate asset cannot find source container's mp4")
    void generateAssetSourceContainerMp4NotFound() {
        EditRequest editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        UUID newRecordingId = UUID.randomUUID();
        String sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        doThrow(new NotFoundException("MP4 file not found in container " + sourceContainer))
            .when(azureIngestStorageService).getMp4FileName(sourceContainer);

        String message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: MP4 file not found in container " + sourceContainer);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verifyNoMoreInteractions(azureIngestStorageService);
    }

}
