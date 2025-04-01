package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlobInputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureFinalStorageService.class)
public class AzureFinalStorageServiceTest {
    @MockitoBean
    private BlobServiceClient finalStorageClient;

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private PagedIterable<BlobItem> pagedIterable;

    @Autowired
    private AzureFinalStorageService azureFinalStorageService;

    private static MockedStatic<DocumentBuilderFactory> documentBuilderFactoryMock;


    
    @BeforeEach
    void setUp() {
        when(finalStorageClient.getBlobContainerClient("test-container")).thenReturn(blobContainerClient);
        when(blobContainerClient.listBlobs()).thenReturn(pagedIterable);
    }

    @BeforeAll
    static void setUpAll() {
        documentBuilderFactoryMock = mockStatic(DocumentBuilderFactory.class);
    }

    @AfterAll
    static void tearDownAll(){
        if (documentBuilderFactoryMock != null) {
            documentBuilderFactoryMock.close();
        }
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

    @Test
    void doesBlobExistsContainerNotFound() {
        when(blobContainerClient.exists()).thenReturn(false);

        assertFalse(azureFinalStorageService.doesBlobExist("test-container", "video.mp4"));
    }

    @Test
    @DisplayName("Should get the recording's duration from .mpd file when it exists in storage")
    void getRecordingDurationSuccess() throws Exception {
        var id = UUID.randomUUID();
        var containerName = id.toString();
        var blobItem = mock(BlobItem.class);
        when(blobItem.getName()).thenReturn("index.mpd");
        var blobClient = mock(BlobClient.class);

        when(finalStorageClient.getBlobContainerClient(containerName)).thenReturn(blobContainerClient);
        when(blobContainerClient.exists()).thenReturn(true);
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));
        when(blobContainerClient.getBlobClient("index.mpd")).thenReturn(blobClient);
        when(blobClient.openInputStream()).thenReturn(mock(BlobInputStream.class));

        var element = mockDocumentElement("PT3M");
        var result = azureFinalStorageService.getRecordingDuration(id);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(Duration.ofMinutes(3));

        verify(blobClient, times(1)).openInputStream();
        verify(element, times(1)).getAttribute("mediaPresentationDuration");
    }

    @Test
    @DisplayName("Should return null duration when there is no .mpd file")
    void getRecordingDurationMpdNotFound() {
        var id = UUID.randomUUID();
        var containerName = id.toString();
        var blobItem = mock(BlobItem.class);
        when(blobItem.getName()).thenReturn("index.mp4");
        var blobClient = mock(BlobClient.class);

        when(finalStorageClient.getBlobContainerClient(containerName)).thenReturn(blobContainerClient);
        when(blobContainerClient.exists()).thenReturn(true);
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        var result = azureFinalStorageService.getRecordingDuration(id);

        assertThat(result).isNull();

        verify(blobClient, never()).openInputStream();
    }

    @Test
    @DisplayName("Should return null duration when encountered and error")
    void getRecordingDurationExceptionReturnsNull() throws Exception {
        var id = UUID.randomUUID();
        var containerName = id.toString();
        var blobItem = mock(BlobItem.class);
        when(blobItem.getName()).thenReturn("index.mpd");
        var blobClient = mock(BlobClient.class);

        when(finalStorageClient.getBlobContainerClient(containerName)).thenReturn(blobContainerClient);
        when(blobContainerClient.exists()).thenReturn(true);
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));
        when(blobContainerClient.getBlobClient("index.mpd")).thenReturn(blobClient);
        doThrow(RuntimeException.class).when(blobClient).openInputStream();

        var element = mockDocumentElement("PT3M");
        var result = azureFinalStorageService.getRecordingDuration(id);

        assertThat(result).isNull();

        verify(blobClient, times(1)).openInputStream();
        verify(element, never()).getAttribute(any());
    }

    private Element mockDocumentElement(String duration) throws Exception {
        var factory = mock(DocumentBuilderFactory.class);
        var builder = mock(DocumentBuilder.class);

        when(DocumentBuilderFactory.newInstance()).thenReturn(factory);
        when(factory.newDocumentBuilder()).thenReturn(builder);

        var element = mock(Element.class);
        var document = mock(Document.class);

        when(builder.parse(any(InputStream.class))).thenReturn(document);
        when(document.getDocumentElement()).thenReturn(element);
        when(element.getAttribute("mediaPresentationDuration")).thenReturn(duration);

        return element;
    }
}
