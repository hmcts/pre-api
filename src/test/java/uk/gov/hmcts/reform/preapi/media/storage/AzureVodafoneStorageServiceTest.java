package uk.gov.hmcts.reform.preapi.media.storage;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.specialized.BlobInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.config.AzureConfiguration;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AzureVodafoneStorageService.class)
public class AzureVodafoneStorageServiceTest {
    @MockitoBean
    private BlobServiceClient vodafoneStorageClient;

    @MockitoBean
    private AzureConfiguration azureConfiguration;

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private PagedIterable<BlobItem> pagedIterable;

    @Mock
    private BlobClient blobClient;

    @Autowired
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @BeforeEach
    void setUp() {
        when(azureConfiguration.isUsingManagedIdentity()).thenReturn(false);
        when(vodafoneStorageClient.getBlobContainerClient("test-container")).thenReturn(blobContainerClient);
        when(blobContainerClient.listBlobs()).thenReturn(pagedIterable);
    }

    @Test
    void fetchBlobNames() {
        var xmlItem = mock(BlobItem.class);
        when(xmlItem.getName()).thenReturn("testfile.xml");
        var vidItem = mock(BlobItem.class);
        when(vidItem.getName()).thenReturn("testfile.mp4");
        when(pagedIterable.stream()).thenReturn(Stream.of(xmlItem, vidItem));

        assertEquals(List.of("testfile.xml"), azureVodafoneStorageService.fetchBlobNames("test-container"));
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
