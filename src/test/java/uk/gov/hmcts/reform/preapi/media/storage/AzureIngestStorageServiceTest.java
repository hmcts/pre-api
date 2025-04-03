package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.polling.SyncPoller;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobCopyInfo;
import com.azure.storage.blob.models.BlobItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private SyncPoller<BlobCopyInfo, Void> poller;

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
    void getStorageAccountName() {
        when(ingestStorageClient.getAccountName()).thenReturn("test-account-name");

        String storageAccountName = azureIngestStorageService.getStorageAccountName();

        assertThat(storageAccountName).isEqualTo("test-account-name");
    }

    @Test
    void getBlobUrlWithSasForCopySuccess() {
        var blobItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        var blobClient = mock(BlobClient.class);
        when(blobClient.getBlobUrl()).thenReturn("example.com/index.mp4");
        when(blobClient.generateSas(any())).thenReturn("exampleSasToken");
        when(blobContainerClient.getBlobClient("index.mp4")).thenReturn(blobClient);
        when(blobItem.getName()).thenReturn("index.mp4");
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of(blobItem));

        String blobUrlWithSas = azureIngestStorageService.getBlobUrlWithSasForCopy("test-container", "index.mp4");

        assertThat(blobUrlWithSas).isEqualTo("example.com/index.mp4?exampleSasToken");
    }

    @Test
    void getBlobUrlWithSasForCopyBlobNotFound() {
        when(blobContainerClient.exists()).thenReturn(true);
        when(pagedIterable.stream()).thenAnswer(inv -> Stream.of());

        String message = assertThrows(
            NotFoundException.class,
            () -> azureIngestStorageService.getBlobUrlWithSasForCopy("test-container", "index.mp4")
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
}
