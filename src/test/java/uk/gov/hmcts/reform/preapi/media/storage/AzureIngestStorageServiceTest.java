package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.UserDelegationKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureIngestStorageService.class)
public class AzureIngestStorageServiceTest {
    @MockitoBean
    private BlobServiceClient ingestStorageClient;

    @MockitoBean
    private AzureConfiguration azureConfiguration;

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private PagedIterable<BlobItem> pagedIterable;

    @Mock
    private SyncPoller<BlobCopyInfo, Void> poller;

    @Autowired
    private AzureIngestStorageService azureIngestStorageService;
    @Autowired
    private BlobServiceClient blobServiceClient;

    @BeforeEach
    void setUp() {
        when(ingestStorageClient.getBlobContainerClient("test-container")).thenReturn(blobContainerClient);
        when(blobContainerClient.listBlobs()).thenReturn(pagedIterable);
    }

    @Test
    void doesValidAssetExistTrueIsmFound() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("video.ism");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertTrue(azureIngestStorageService.doesValidAssetExist("test-container"));
    }

    @Test
    void doesValidAssetExistTrueGcStateFound() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("gc_state");
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of(blobItem));

        assertTrue(azureIngestStorageService.doesValidAssetExist("test-container"));
    }

    @Test
    void doesValidAssetExistFalse() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("something-else.mp4");
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of(blobItem));

        assertFalse(azureIngestStorageService.doesValidAssetExist("test-container"));
    }

    @Test
    void uploadBlobSuccess() throws IOException {
        var tempFile = Files.createTempFile("test", ".mp4");
        var localFileName = tempFile.toString();
        var containerName = "test-container";
        var uploadFileName = "uploaded.mp4";

        when(ingestStorageClient.createBlobContainerIfNotExists(containerName)).thenReturn(blobContainerClient);
        var mockBlobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(uploadFileName)).thenReturn(mockBlobClient);

        assertTrue(azureIngestStorageService.uploadBlob(localFileName, containerName, uploadFileName));

        verify(mockBlobClient, times(1)).upload(any(FileInputStream.class), anyLong(), eq(true));

        // clean up
        Files.deleteIfExists(tempFile);
    }

    @Test
    void uploadBlobFileNotFound() {
        var localFileName = "test.mp4";
        var containerName = "test-container";
        var uploadFileName = "uploaded.mp4";

        when(ingestStorageClient.createBlobContainerIfNotExists(containerName)).thenReturn(blobContainerClient);
        var mockBlobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(uploadFileName)).thenReturn(mockBlobClient);

        assertFalse(azureIngestStorageService.uploadBlob(localFileName, containerName, uploadFileName));

        verify(mockBlobClient, never()).upload(any(FileInputStream.class), anyLong(), eq(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void markContainerAsProcessing() {
        var blobItem = mock(BlobItem.class);
        var blobClient = mock(BlobClient.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("video.ism");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));
        when(blobContainerClient.getBlobClient("video.ism")).thenReturn(blobClient);

        azureIngestStorageService.markContainerAsProcessing("test-container");

        verify(blobClient, times(1)).getTags();

        var tagsCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
        verify(blobClient, times(1)).setTags(tagsCaptor.capture());
        assertThat(tagsCaptor.getValue()).containsEntry("status", "processing");
    }

    @Test
    @SuppressWarnings("unchecked")
    void markContainerAsSafeToDelete() {
        var blobItem = mock(BlobItem.class);
        var blobClient = mock(BlobClient.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("video.ism");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));
        when(blobContainerClient.getBlobClient("video.ism")).thenReturn(blobClient);

        azureIngestStorageService.markContainerAsSafeToDelete("test-container");

        verify(blobClient, times(1)).getTags();
        var tagsCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
        verify(blobClient, times(1)).setTags(tagsCaptor.capture());
        assertThat(tagsCaptor.getValue()).containsEntry("status", "safe_to_delete");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetBlobTag() {
        var blobClient = mock(BlobClient.class);
        Map<String, String> tags = new HashMap<>();
        tags.put("existing_key", "existing_value");
        when(blobClient.getTags()).thenReturn(tags);

        azureIngestStorageService.setBlobTag(blobClient, "status", "processing");

        var tagsCaptor = ArgumentCaptor.forClass((Class<Map<String, String>>) (Class<?>) Map.class);
        verify(blobClient, times(1)).setTags(tagsCaptor.capture());
        assertThat(tagsCaptor.getValue()).containsEntry("existing_key", "existing_value");
        assertThat(tagsCaptor.getValue()).containsEntry("status", "processing");
    }

    @Test
    void getBlobUrlForCopySuccess() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        var blobClient = mock(BlobClient.class);
        when(blobClient.getBlobUrl()).thenReturn("example.com/index.mp4");
        when(blobClient.generateSas(any())).thenReturn("exampleSasToken");
        when(blobContainerClient.getBlobClient("index.mp4")).thenReturn(blobClient);
        when(blobItem.getName()).thenReturn("index.mp4");
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of(blobItem));

        String blobUrlWithSas = azureIngestStorageService.getBlobUrlForCopy("test-container", "index.mp4");

        assertThat(blobUrlWithSas).isEqualTo("example.com/index.mp4?exampleSasToken");
    }

    @Test
    void getBlobUrlForCopySuccessForManagedIdentity() {
        when(azureConfiguration.isUsingManagedIdentity()).thenReturn(true);
        when(ingestStorageClient.getUserDelegationKey(any(), any())).thenReturn(mock(UserDelegationKey.class));
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        var blobClient = mock(BlobClient.class);
        when(blobClient.getBlobUrl()).thenReturn("example.com/index.mp4");
        when(blobClient.getBlobUrl()).thenReturn("example.com/index.mp4");
        when(blobContainerClient.getBlobClient("index.mp4")).thenReturn(blobClient);
        when(blobItem.getName()).thenReturn("index.mp4");
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of(blobItem));
        when(blobClient.generateUserDelegationSas(any(), any())).thenReturn("exampleSasToken");

        String blobUrl = azureIngestStorageService.getBlobUrlForCopy("test-container", "index.mp4");

        assertThat(blobUrl).isEqualTo("example.com/index.mp4?exampleSasToken");

        verify(blobContainerClient, never()).generateUserDelegationSas(any(), any());
    }

    @Test
    void getBlobUrlForCopyBlobNotFound() {
        when(blobContainerClient.exists()).thenReturn(true);
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of());

        String message = assertThrows(
            NotFoundException.class,
            () -> azureIngestStorageService.getBlobUrlForCopy("test-container", "index.mp4")
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Blob in container test-container");
    }

    @Test
    void copyBlobDestContainerNotFoundSuccess() {
        var sourceUrl = "example.com/index.mp4?sasToken";
        when(blobContainerClient.exists()).thenReturn(false);
        var blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient("index.mp4")).thenReturn(blobClient);
        when(blobClient.beginCopy(sourceUrl, null)).thenReturn(poller);

        azureIngestStorageService.copyBlob("test-container", "index.mp4", sourceUrl);

        verify(blobContainerClient, times(1)).create();
        verify(blobClient, times(1)).beginCopy(sourceUrl, null);
    }

    @Test
    void copyBlobDestContainerFoundSuccess() {
        var sourceUrl = "example.com/index.mp4?sasToken";
        when(blobContainerClient.exists()).thenReturn(true);
        var blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient("index.mp4")).thenReturn(blobClient);
        when(blobClient.beginCopy(sourceUrl, null)).thenReturn(poller);

        azureIngestStorageService.copyBlob("test-container", "index.mp4", sourceUrl);

        verify(blobContainerClient, never()).create();
        verify(blobClient, times(1)).beginCopy(sourceUrl, null);
    }

    @Test
    void copyBlobSkipOverwrite() {
        when(blobContainerClient.exists()).thenReturn(true);
        var blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient("index.mp4")).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);

        var sourceUrl = "example.com/index.mp4?sasToken";
        azureIngestStorageService.copyBlobOverwritable("test-container", "index.mp4", sourceUrl, false);

        verify(blobClient, never()).beginCopy(sourceUrl, null);

    }

    @Test
    void returnsTrueIfSectionFileIsPresent() {
        var blobItem = mock(BlobItem.class);
        when(blobServiceClient.getBlobContainerClient(
            "5b828a09-224a-4ca7-9657-f32f3981eef7")).thenReturn(blobContainerClient);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("0/section");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertTrue(azureIngestStorageService.sectionFileExist("5b828a09-224a-4ca7-9657-f32f3981eef7"));
    }

    @Test
    void returnsFalseIfSectionFileIsNotPresent() {
        var blobItem = mock(BlobItem.class);
        when(blobServiceClient.getBlobContainerClient(
            "5b828a09-224a-4ca7-9657-f32f3981eef7")).thenReturn(blobContainerClient);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("not-the-section-file");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertFalse(azureIngestStorageService.sectionFileExist("5b828a09-224a-4ca7-9657-f32f3981eef7"));
    }

    @Test
    void returnsFalseIfTheContainerToCheckDoesNotExist() {
        when(blobContainerClient.exists()).thenReturn(false);
        when(blobServiceClient.getBlobContainerClient(
            "5b828a09-224a-4ca7-9657-f32f3981eef7")).thenReturn(blobContainerClient);

        assertFalse(azureIngestStorageService.sectionFileExist("5b828a09-224a-4ca7-9657-f32f3981eef7"));
    }
}
