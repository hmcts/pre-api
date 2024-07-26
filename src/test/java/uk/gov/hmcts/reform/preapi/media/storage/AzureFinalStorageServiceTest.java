package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureFinalStorageService.class)
public class AzureFinalStorageServiceTest {
    @MockBean
    private BlobServiceClient finalStorageClient;

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private PagedIterable<BlobItem> pagedIterable;

    @Autowired
    private AzureFinalStorageService azureFinalStorageService;

    @BeforeEach
    void setUp() {
        when(finalStorageClient.getBlobContainerClient("test-container")).thenReturn(blobContainerClient);
        when(blobContainerClient.listBlobs()).thenReturn(pagedIterable);
    }

    @Test
    void doesIsmFileExistTrue() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("video.ism");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertTrue(azureFinalStorageService.doesIsmFileExist("test-container"));
    }

    @Test
    void doesIsmFileExistFalse() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("video.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertFalse(azureFinalStorageService.doesIsmFileExist("test-container"));
    }

    @Test
    void doesIsmFileExistEmptyContainer() {
        when(blobContainerClient.exists()).thenReturn(true);
        when(pagedIterable.stream()).thenReturn(Stream.of());

        assertFalse(azureFinalStorageService.doesIsmFileExist("test-container"));
    }

    @Test
    void doesIsmExistContainerNotFound() {
        when(blobContainerClient.exists()).thenReturn(false);

        assertFalse(azureFinalStorageService.doesIsmFileExist("test-container"));
    }

    @Test
    void doesBlobExistsTrue() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("video.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertTrue(azureFinalStorageService.doesBlobExist("test-container", "video.mp4"));
    }

    @Test
    void doesBlobExistsTrueIgnoreCase() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("video.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertTrue(azureFinalStorageService.doesBlobExist("test-container", "VIDEO.mp4"));
    }

    @Test
    void doesBlobExistsFalse() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(blobItem.getName()).thenReturn("video.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertFalse(azureFinalStorageService.doesBlobExist("test-container", "video.ism"));
    }

    @Test
    void doesBlobExistsContainerNotFound() {
        when(blobContainerClient.exists()).thenReturn(false);

        assertFalse(azureFinalStorageService.doesBlobExist("test-container", "video.mp4"));
    }
}
