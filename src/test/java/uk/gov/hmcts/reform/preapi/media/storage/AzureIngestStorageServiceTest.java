package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private PagedIterable<BlobItem> pagedIterable;

    @Autowired
    private AzureIngestStorageService azureIngestStorageService;

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

        verify(mockBlobClient, times(1)).upload(any(InputStream.class), anyLong(), eq(true));

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
}
