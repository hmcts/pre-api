package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlobInputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureVodafoneStorageService.class)
public class AzureVodafoneStorageServiceTest {
    @MockitoBean
    private BlobServiceClient vodaStorageClient;

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private PagedIterable<BlobItem> pagedIterable;

    @Mock
    private BlobClient blobClient;

    @Autowired
    private AzureVodafoneStorageService azureVodafoneStorageService;

    private static MockedStatic<DocumentBuilderFactory> documentBuilderFactoryMock;

    @BeforeEach
    void setUp() {
        when(vodaStorageClient.getBlobContainerClient("test-container")).thenReturn(blobContainerClient);
        when(blobContainerClient.listBlobs()).thenReturn(pagedIterable);

        documentBuilderFactoryMock = mockStatic(DocumentBuilderFactory.class);
    }

    @AfterAll
    static void tearDownAll(){
        if (documentBuilderFactoryMock != null) {
            documentBuilderFactoryMock.close();
        }
    }

    @Test
    void fetchBlobNames() {
        var xmlItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(xmlItem.getName()).thenReturn("testfile.xml");
        var vidItem = mock(BlobItem.class);
        when(blobContainerClient.exists()).thenReturn(true);
        when(vidItem.getName()).thenReturn("testfile.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(xmlItem, vidItem));

        assertEquals(azureVodafoneStorageService.fetchBlobNames("test-container"), List.of("testfile.xml"));
    }

    @Test
    void fetchSingleXmlBlobSuccess() {
        when(blobContainerClient.getBlobClient("testfile.xml")).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(true);
        when(blobClient.openInputStream()).thenReturn(mock(BlobInputStream.class));


        assertEquals(
            azureVodafoneStorageService.fetchSingleXmlBlob("test-container", "testfile.xml"),
            new InputStreamResource(blobClient.openInputStream(), "testfile.xml")
        );
    }

    @Test
    void fetchSingleXmlBlobFailExists() {
        when(blobContainerClient.getBlobClient("testfile.xml")).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);


        assertNull(azureVodafoneStorageService.fetchSingleXmlBlob("test-container", "testfile.xml"));
    }

    @Test
    void fetchSingleXmlBlobFailException() {
        when(blobContainerClient.getBlobClient("testfile.xml")).thenThrow(new RuntimeException());


        assertNull(azureVodafoneStorageService.fetchSingleXmlBlob("test-container", "testfile.xml"));
    }
}
