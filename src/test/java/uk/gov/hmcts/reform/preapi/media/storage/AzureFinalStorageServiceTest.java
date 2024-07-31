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
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(blobItem.getName()).thenReturn("video.ism");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));
        when(blobContainerClient.exists()).thenReturn(true);

        assertTrue(azureFinalStorageService.doesIsmFileExist("test-container"));
    }

    @Test
    void doesIsmFileExistFalse() {
        var blobItem = mock(BlobItem.class);
        when(blobItem.getName()).thenReturn("video.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertFalse(azureFinalStorageService.doesIsmFileExist("test-container"));
    }

    @Test
    void doesIsmFileExistEmptyContainer() {
        when(pagedIterable.stream()).thenReturn(Stream.of());

        assertFalse(azureFinalStorageService.doesIsmFileExist("test-container"));
    }

    @Test
    void doesBlobExistsTrue() {
        var blobItem = mock(BlobItem.class);
        when(blobItem.getName()).thenReturn("video.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertTrue(azureFinalStorageService.doesBlobExist("test-container", "video.mp4"));
    }

    @Test
    void doesBlobExistsTrueIgnoreCase() {
        var blobItem = mock(BlobItem.class);
        when(blobItem.getName()).thenReturn("video.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertTrue(azureFinalStorageService.doesBlobExist("test-container", "VIDEO.mp4"));
    }

    @Test
    void doesBlobExistsFalse() {
        var blobItem = mock(BlobItem.class);
        when(blobItem.getName()).thenReturn("video.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        assertFalse(azureFinalStorageService.doesBlobExist("test-container", "video.ism"));
    }

    @Test
    void getMp4FileNameMultipleMp4s() {
        var blobItem1 = mock(BlobItem.class);
        when(blobItem1.getName()).thenReturn("video1.mp4");
        var blobItem2 = mock(BlobItem.class);
        when(blobItem2.getName()).thenReturn("something-else.txt");
        var blobItem3 = mock(BlobItem.class);
        when(blobItem3.getName()).thenReturn("video2.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem1, blobItem2, blobItem2));

        var mp4FileName = azureFinalStorageService.getMp4FileName("test-container");
        assertEquals("video1.mp4", mp4FileName);
    }

    @Test
    void getMp4FileNameNoMp4s() {
        var blobItem1 = mock(BlobItem.class);
        when(blobItem1.getName()).thenReturn("video1.docx");
        var blobItem2 = mock(BlobItem.class);
        when(blobItem2.getName()).thenReturn("something-else.txt");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem1, blobItem2));

        var message = assertThrows(
            NotFoundException.class,
            () -> azureFinalStorageService.getMp4FileName("test-container")
        ).getMessage();

        assertEquals("Not found: MP4 file not found in container test-container", message);
    }

    @Test
    void doesContainerExistTrue() {
        when(blobContainerClient.exists()).thenReturn(true);

        assertTrue(azureFinalStorageService.doesContainerExist("test-container"));
    }

    @Test
    void doesContainerExistFalse() {
        when(blobContainerClient.exists()).thenReturn(false);

        assertFalse(azureFinalStorageService.doesContainerExist("test-container"));
    }

    @Test
    void tryGetMp4FileNameSuccess() {
        var blobItem1 = mock(BlobItem.class);
        when(blobItem1.getName()).thenReturn("video1.mp4");
        var blobItem2 = mock(BlobItem.class);
        when(blobItem2.getName()).thenReturn("something-else.txt");
        var blobItem3 = mock(BlobItem.class);
        when(blobItem3.getName()).thenReturn("video2.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem1, blobItem2, blobItem2));

        var mp4FileName = azureFinalStorageService.tryGetMp4FileName("test-container");
        assertEquals("video1.mp4", mp4FileName);
    }

    @Test
    void tryGetMp4FileNameNoMp4s() {
        var blobItem1 = mock(BlobItem.class);
        when(blobItem1.getName()).thenReturn("video1.docx");
        var blobItem2 = mock(BlobItem.class);
        when(blobItem2.getName()).thenReturn("something-else.txt");
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem1, blobItem2));

        assertThat(azureFinalStorageService.tryGetMp4FileName("test-container"))
            .isNull();
    }
}
