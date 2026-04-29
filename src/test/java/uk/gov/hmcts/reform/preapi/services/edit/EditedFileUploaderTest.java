package uk.gov.hmcts.reform.preapi.services.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditedFileUploader.class)
class EditedFileUploaderTest {

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private EditRequest editRequest;

    @MockitoBean
    private Recording mockRecording;

    @Autowired
    public EditedFileUploader underTest;

    @BeforeEach
    void setUp() {
        when(editRequest.getSourceRecording()).thenReturn(mockRecording);
    }

    @Test
    @DisplayName("Should throw error when source recording does not have a filename set")
    void performEditDownloadFailure() {
        UUID recordingId = UUID.randomUUID();
        String inputFilename = "filename";
        when(mockRecording.getFilename()).thenReturn(inputFilename);
        when(mockRecording.getId()).thenReturn(recordingId);

        when(azureFinalStorageService.downloadBlob(recordingId.toString(), inputFilename, inputFilename))
            .thenReturn(false);

        String message = assertThrows(
            UnknownServerException.class,
            () -> underTest.downloadInputFile(editRequest, inputFilename)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Unknown Server Exception: Error occurred when attempting to download file: "
                           + inputFilename);

        verify(azureFinalStorageService, times(1))
            .downloadBlob(recordingId.toString(), inputFilename, inputFilename);
        verifyNoMoreInteractions(azureFinalStorageService);
        verifyNoMoreInteractions(azureIngestStorageService);
    }
}
