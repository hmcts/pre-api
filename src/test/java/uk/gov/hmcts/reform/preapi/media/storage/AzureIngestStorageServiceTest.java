package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureIngestStorageService.class)
public class AzureIngestStorageServiceTest {
    @MockBean
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
}
